package scaleview.agent.stub;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import scaleview.agent.command.CommandThread;
import scaleview.agent.event.ProfilingEvent;
import scaleview.agent.event.ProfilingEventHandler;
import scaleview.agent.event.ProfilingThread;
import scaleview.agent.util.Logging;
import scaleview.agent.util.Pair;

public class ProfilingStub {

	private static final Logger LOG = Logging.getLogger(ProfilingStub.class.getName());

	public static class Range implements Comparable<Range> {

		private int left;
		private int right;
		private long methodId;

		@Override
		public int compareTo(Range o) {
			if (left < o.left) {
				return -1;
			} else if (left > o.left) {
				return 1;
			} else {
				return right - o.right;
			}
		}

		public boolean isInRange(int line) {
			return left <= line && right >= line;
		}

		public int getLeft() {
			return left;
		}

		public void setLeft(int left) {
			this.left = left;
		}

		public int getRight() {
			return right;
		}

		public void setRight(int right) {
			this.right = right;
		}

		public long getMethodId() {
			return methodId;
		}

		public void setMethodId(long methodId) {
			this.methodId = methodId;
		}
	}

	public static class SystemMethodId extends Pair<Long, Integer> {

		public SystemMethodId(Long a, Integer b) {
			super(a, b);
		}

		@Override
		public int hashCode() {
			return getRight();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof SystemMethodId) {
				SystemMethodId oo = (SystemMethodId) o;
				return oo.getLeft().longValue() == getLeft().longValue()
						&& oo.getRight().intValue() == getRight().intValue();
			}
			return false;
		}

		public Long getLeft() {
			return super.getLeft();
		}

		public Integer getRight() {
			return super.getRight();
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("SystemMethodId [getLeft()=").append(getLeft()).append(", getRight()=").append(getRight())
					.append("]");
			return builder.toString();
		}

	}

	public static ProfilingEventHandler METHOD_ENTRY;
	public static AtomicBoolean LE_ENABLED = new AtomicBoolean(false);
	public static AtomicInteger CURRENT_RUN = new AtomicInteger(0);
	public static CommandThread CMD_THREAD;
	public static Instrumentation INSTRUMENTATION;
	public static Map<Long, Set<Range>> SYSTEM_METHODS_DECLARATIONS = new ConcurrentHashMap<>();
	public static Map<SystemMethodId, Long> SYSTEM_METHODS_CACHE = new ConcurrentHashMap<>();
	public static Map<Long, LinkedList<Long>> CALL_CHAINS = new ConcurrentHashMap<>();
	public static Map<Long, Map<Long, Integer>> METHOD_EXECUTION_COUNT = new ConcurrentHashMap<>();
	// any number of readers can own this lock, but the writer blocks them all...
	public static final ReentrantReadWriteLock METHOD_EXECUTION_COUNT_LOCK = new ReentrantReadWriteLock();
	public static final List<String> INCLUSIONS = new ArrayList<>();
	public static final List<String> EXCLUSIONS = new ArrayList<>();

	public static Map<Long, Map<Long, Integer>> switchMethodExectionData() {
		METHOD_EXECUTION_COUNT_LOCK.writeLock().lock();
		try {
			Map<Long, Map<Long, Integer>> old = METHOD_EXECUTION_COUNT;
			METHOD_EXECUTION_COUNT = new ConcurrentHashMap<>();
			return old;
		} finally {
			METHOD_EXECUTION_COUNT_LOCK.writeLock().unlock();
		}
	}

	public static void updateMethodExecutionForRun(long methodId) {
		long id = Thread.currentThread().getId();
		METHOD_EXECUTION_COUNT_LOCK.readLock().lock();
		try {
			Map<Long, Integer> counts = METHOD_EXECUTION_COUNT.get(id);
			if (counts == null) {
				counts = new HashMap<>();
				counts.put(methodId, 1);
				METHOD_EXECUTION_COUNT.put(id, counts);
			} else {
				Integer total = counts.get(methodId);
				if (total == null) {
					counts.put(methodId, 1);
				} else {
					counts.put(methodId, total + 1);
				}

			}
		} finally {
			METHOD_EXECUTION_COUNT_LOCK.readLock().unlock();
		}
	}

	public static void updateRangesForMethod(String klass, String name, long methodId, Pair<Integer, Integer> ranges) {
		String id = klass + "." + name;
		Range newOne = new Range();
		newOne.setLeft(ranges.getLeft());
		newOne.setRight(ranges.getRight());
		newOne.setMethodId(methodId);
		long hashCode = id.hashCode();
		Set<Range> existing = SYSTEM_METHODS_DECLARATIONS.get(hashCode);
		if (existing == null) {
			existing = new TreeSet<>();
			existing.add(newOne);
			SYSTEM_METHODS_DECLARATIONS.put(hashCode, existing);
		} else {
			existing.add(newOne);
		}
	}

	public static Long lookupByLine(String klass, String name, int lineNumber) {
		String id = klass + "." + name;
		long hashCode = id.hashCode();
		Set<Range> existing = SYSTEM_METHODS_DECLARATIONS.get(hashCode);
		if (existing != null) {
			if (existing.size() == 1) {
				for (Range r : existing) {
					return r.getMethodId();
				}
			} else {
				for (Range r : existing) {
					if (r.isInRange(lineNumber))
						return r.getMethodId();
				}
			}
		}
		return null;

	}

	public static boolean isIncluded(String name) {
		for (String p : INCLUSIONS) {
			if (name.startsWith(p))
				return true;
		}
		return false;
	}

	public static void clearCalls() {
	}

	public static long getOrAddFromChain() {
		long id = Thread.currentThread().getId();
		LinkedList<Long> current = CALL_CHAINS.get(id);
		long stamp = System.nanoTime();
		if (current == null) {
			current = new LinkedList<Long>();
			current.add(stamp);
			CALL_CHAINS.put(id, current);
		} else {
			current.add(stamp);
			int size = current.size();
			if (size >= 2) {
				return current.get(size - 2);
			} else {
				return current.get(size - 1);
			}
		}
		return stamp;
	}

	public static void removeFromChain() {
		long id = Thread.currentThread().getId();
		LinkedList<Long> current = CALL_CHAINS.get(id);
		if (current != null && current.size() > 0) {
			current.removeLast();
		}
	}

	public static boolean isNotIncluded(String name) {
		for (String p : EXCLUSIONS) {
			if (name.startsWith(p))
				return true;
		}
		return false;
	}

	public static long lookupUsingStackTrace() {
		for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
			// first method that matches the execution
			if (isIncluded(element.getClassName())) {
				Long methodId = lookupByLine(element.getClassName(), element.getMethodName(), element.getLineNumber());
				if (methodId == null) {
					// verbose, but maybe practical since it allows us to see what fails...
					LOG.severe("No range was found for " + element.getClassName() + "." + element.getMethodName()
							+ " : " + element.getLineNumber());
					return 0L;
				}
				try {
					SystemMethodId key = new SystemMethodId(methodId, element.getLineNumber());
					Long recorded = SYSTEM_METHODS_CACHE.get(key);
					if (recorded == null) {
						long next = System.nanoTime();
						SYSTEM_METHODS_CACHE.put(key, next);
						METHOD_ENTRY.processEvent(new ProfilingEvent.SystemMethodRegisterEvent(
								Thread.currentThread().getId(), next, methodId, element.getLineNumber()));
						return next;
					} else {
						return recorded;
					}
				} catch (Exception e) {
					Logging.exception(LOG, e, "Error when trying to register new system method");
				}
			}
		}
		// this is not an actual error, not sure how often it happens but is likely due
		// to some
		// code of a library called from no application thread, like when the system is
		// starting or something like that. I cannot do much dynamically in this
		// scenario.
		return 0L;
	}

	public static ProfilingThread buildShutdownHookThread() {
		return new ProfilingThread(
				new Runnable() {
					@Override
					public void run() {
						LOG.severe("Shutting down profiling stub...");
						// disable this guy, no more events comming iin
						ProfilingStub.LE_ENABLED.set(false);
						// we have to write the last events...
						CountDownLatch latch = new CountDownLatch(1);
						LOG.severe("Dumping last batch of events...");
						CommandThread.dumpMethods(switchMethodExectionData(), CURRENT_RUN.get(), latch);
						// and wait...
						try {
							latch.await();
						} catch (Exception e) {
						} finally {
							LOG.severe("Done dumping last batch of events...");
						}
						// now close the bussiness
						ProfilingStub.METHOD_ENTRY.closeStreams();
						// one by one...
						if (ProfilingStub.METHOD_ENTRY != null) {
							try {
								ProfilingStub.METHOD_ENTRY.shutdown();
							} catch (Exception e) {
								Logging.exception(LOG, e, "Error when shutting down method pool...");
							}
						}
						// also this guy
						if (ProfilingStub.CMD_THREAD != null) {
							ProfilingStub.CMD_THREAD.shutdown();
						}
					}
				},
				"shutdownthread",
				Integer.MAX_VALUE);
	}

}
