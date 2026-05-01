package scaleview.agent.event;

import java.io.IOException;
import java.io.Writer;

import scaleview.agent.event.ProfilingTask.BlockEntry;
import scaleview.agent.event.ProfilingTask.MethodEntry;
import scaleview.agent.event.ProfilingTask.MethodRegister;
import scaleview.agent.event.ProfilingTask.SystemMethodRegister;

public abstract class ProfilingEvent {

	public static final String EVT_SEP = " ";

	protected long threadId;

	public ProfilingEvent(long tid) {
		this.threadId = tid;
	}

	public void writeEvent(Writer writer) throws IOException {
		StringBuilder ev = formatEvent();
		if (ev != null)
			writer.write(ev.append("\n").toString());
	}

	public abstract StringBuilder formatEvent();

	public abstract ProfilingEvent fromString(String event);

	public abstract <T> ProfilingTask<T> asProfilingTask();

	public abstract String getFilterName();

	public long getThreadId() {
		return this.threadId;
	}

	public static class MethodEntryEvent extends ProfilingEvent {

		public static final String EVT_PROF = "E";

		private long methodId;
		private int run;
		private int count;

		public MethodEntryEvent(int tid) {
			super(tid);
		}

		public MethodEntryEvent(long tid, long mid, int r, int c) {
			super(tid);
			methodId = mid;
			run = r;
			count = c;
		}

		@Override
		public StringBuilder formatEvent() {
			// everything else goes here...
			return new StringBuilder().append(EVT_PROF)
					.append(EVT_SEP)
					.append(run)
					.append(EVT_SEP)
					.append(threadId)
					.append(EVT_SEP)
					.append(methodId)
					.append(EVT_SEP)
					.append(count);
		}

		public void resetMethodId(long newId) {
			methodId = newId;
		}

		public long getMethodId() {
			return methodId;
		}

		public int getRun() {
			return run;
		}

		public int getCount() {
			return count;
		}

		@Override
		public MethodEntry asProfilingTask() {
			return new ProfilingTask.MethodEntry(this);
		}

		@Override
		public MethodEntryEvent fromString(String event) {
			String[] pieces = event.split(EVT_SEP);
			return new MethodEntryEvent(Integer.parseInt(pieces[2]), Long.parseLong(pieces[3]),
					Integer.parseInt(pieces[1]), Integer.parseInt(pieces[4]));
		}

		@Override
		public String getFilterName() {
			return EVT_PROF;
		}

	}

	public static class MethodRegisterEvent extends ProfilingEvent {

		public static final String EVT_PROF = "R";
		public static final String NO_ARGS = "0";

		private String className;
		private String methodName;
		private String signature;
		private long methodNum;

		public MethodRegisterEvent(int tid) {
			super(tid);
		}

		public MethodRegisterEvent(long tid, String cl, String n, String s, long num) {
			super(tid);
			className = cl;
			methodName = n;
			signature = s;
			methodNum = num;
		}

		@Override
		public StringBuilder formatEvent() {
			return new StringBuilder().append(EVT_PROF)
					.append(EVT_SEP)
					.append(methodNum)
					.append(EVT_SEP)
					.append(className)
					.append(EVT_SEP)
					.append(methodName)
					.append(EVT_SEP)
					.append(signature);
		}

		@Override
		public MethodRegister asProfilingTask() {
			return new ProfilingTask.MethodRegister(this);
		}

		@Override
		public MethodRegisterEvent fromString(String event) {
			String[] pieces = event.split(EVT_SEP);
			return new MethodRegisterEvent(0, pieces[2], pieces[3], pieces[4].length() <= 3 ? "()" : pieces[4],
					Long.parseLong(pieces[1]));
		}

		public long getMethodNum() {
			return methodNum;
		}

		public String getClassName() {
			return className;
		}

		public String getMethodName() {
			return methodName;
		}

		public String getSignature() {
			return getMethodName() + signature;
		}

		public void setMethodNum(int methodNum) {
			this.methodNum = methodNum;
		}

		@Override
		public String getFilterName() {
			return EVT_PROF;
		}

	}

	public static class SystemMethodRegisterEvent extends ProfilingEvent {

		public static final String EVT_PROF = "S";
		public static final String NO_ARGS = "0";

		private long methodNum;
		private long appMethod;
		private int lineNumber;

		public SystemMethodRegisterEvent(int tid) {
			super(tid);
		}

		public SystemMethodRegisterEvent(long tid, long num, long app, int ln) {
			super(tid);
			methodNum = num;
			appMethod = app;
			lineNumber = ln;
		}

		@Override
		public StringBuilder formatEvent() {
			return new StringBuilder().append(EVT_PROF)
					.append(EVT_SEP)
					.append(methodNum)
					.append(EVT_SEP)
					.append(appMethod)
					.append(EVT_SEP)
					.append(lineNumber);
		}

		@Override
		public SystemMethodRegister asProfilingTask() {
			return new ProfilingTask.SystemMethodRegister(this);
		}

		@Override
		public SystemMethodRegisterEvent fromString(String event) {
			String[] pieces = event.split(EVT_SEP);
			return new SystemMethodRegisterEvent(0, Long.parseLong(pieces[1]), Long.parseLong(pieces[2]),
					Integer.parseInt(pieces[3]));
		}

		public long getMethodNum() {
			return methodNum;
		}

		public int getLineNumber() {
			return lineNumber;
		}

		public long getAppMethod() {
			return appMethod;
		}

		@Override
		public String getFilterName() {
			return EVT_PROF;
		}
	}

	public static class LoopEntryEvent extends ProfilingEvent {

		public static final String EVT_PROF = "L";
		public static final int NO_ID_EVT = 0;

		private long methodId;
		private int lineNumber;
		private int run;
		private long itId;
		private int iterationCount;
		private boolean shouldResolve;
		private long systemMethod;

		public long getMethodId() {
			return methodId;
		}

		public long getSystemMethod() {
			return systemMethod;
		}

		public void setSystemMethod(long systemMethod) {
			this.systemMethod = systemMethod;
		}

		public boolean isShouldResolve() {
			return shouldResolve;
		}

		public void resetMethodId(int newId) {
			methodId = newId;
		}

		public void resetMethodId(long newId) {
			methodId = (int) newId;
		}

		public void resetLineNumber(int newLn) {
			lineNumber = newLn;
		}

		public static class CollapsedLoop {
			private long methodId;
			private int lineNumber;

			public CollapsedLoop(long mid, int lin) {
				this.methodId = mid;
				this.lineNumber = lin;
			}

			@Override
			public int hashCode() {
				return lineNumber;
			}

			@Override
			public boolean equals(Object o) {
				if (!(o instanceof CollapsedLoop))
					return false;
				return lineNumber == ((CollapsedLoop) o).lineNumber && methodId == ((CollapsedLoop) o).methodId;
			}

			public long getMethodId() {
				return methodId;
			}

			public int getLineNumber() {
				return lineNumber;
			}

			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("CollapsedLoop [methodId=").append(methodId).append(", lineNumber=").append(lineNumber)
						.append("]");
				return builder.toString();
			}

		}

		public static class LoopEntryEventId implements Comparable<LoopEntryEventId> {
			private long methodId;
			private int lineNumber;
			private long iterationId;

			public LoopEntryEventId(long mid, int lin, long it) {
				this.methodId = mid;
				this.lineNumber = lin;
				this.iterationId = it;
			}

			@Override
			public int hashCode() {
				return lineNumber;
			}

			@Override
			public boolean equals(Object o) {
				if (!(o instanceof LoopEntryEventId))
					return false;
				return lineNumber == ((LoopEntryEventId) o).lineNumber && methodId == ((LoopEntryEventId) o).methodId
						&& iterationId == ((LoopEntryEventId) o).iterationId;
			}

			public long getMethodId() {
				return methodId;
			}

			public int getLineNumber() {
				return lineNumber;
			}

			public long getIterationId() {
				return iterationId;
			}

			@Override
			public int compareTo(LoopEntryEventId arg0) {
				long a = methodId - arg0.methodId;
				int b = lineNumber - arg0.lineNumber;
				long c = iterationId - arg0.iterationId;
				if (a == 0) {
					if (b == 0) {
						if (c == 0)
							return 0;
						return (int) c;
					}
					return b;
				}
				return a > 0 ? 1 : -1;
			}

		}

		public LoopEntryEvent(int tid) {
			super(tid);
		}

		public LoopEntryEvent(long tid, long m, int ln, int r, long i, boolean sr, long smid, int count) {
			super(tid);
			methodId = m;
			lineNumber = ln;
			run = r;
			itId = i;
			shouldResolve = sr;
			systemMethod = smid;
			iterationCount = count;
		}

		@Override
		public StringBuilder formatEvent() {
			return new StringBuilder().append(EVT_PROF)
					.append(EVT_SEP)
					.append(itId)
					.append(EVT_SEP)
					.append(run)
					.append(EVT_SEP)
					.append(threadId)
					.append(EVT_SEP)
					.append(methodId)
					.append(EVT_SEP)
					.append(lineNumber)
					.append(EVT_SEP)
					.append(shouldResolve)
					.append(EVT_SEP)
					.append(systemMethod)
					.append(EVT_SEP)
					.append(iterationCount);
		}

		public int getIterationCount() {
			return iterationCount;
		}

		public int getLineNumber() {
			return lineNumber;

		}

		public int getRun() {
			return run;
		}

		public long getItId() {
			return itId;
		}

		@Override
		public BlockEntry asProfilingTask() {
			return new ProfilingTask.BlockEntry(this);
		}

		public LoopEntryEvent fromString(String event) {
			String[] pieces = event.split(EVT_SEP);
			return new LoopEntryEvent(Integer.parseInt(pieces[3]), Long.parseLong(pieces[4]),
					Integer.parseInt(pieces[5]), Integer.parseInt(pieces[2]), Long.parseLong(pieces[1]),
					Boolean.parseBoolean(pieces[6]), Long.parseLong(pieces[7]), Integer.parseInt(pieces[8]));
		}

		@Override
		public String getFilterName() {
			return EVT_PROF;
		}

	}
}
