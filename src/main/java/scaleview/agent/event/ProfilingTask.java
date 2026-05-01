package scaleview.agent.event;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import scaleview.agent.util.Logging;

public abstract class ProfilingTask<T> implements Callable<T> {

	protected ProfilingEvent event;

	public ProfilingTask(ProfilingEvent e) {
		this.event = e;
	}

	public abstract T doCall(Writer w);

	public ProfilingEvent getEvent() {
		return event;
	}

	@Override
	public T call() {
		Writer w = ((ProfilingThread) Thread.currentThread()).getWriter();
		try {
			return doCall(w);
		} finally {
			((ProfilingThread) Thread.currentThread()).checkForFlush();
		}
	}

	public static class MethodEntry extends ProfilingTask<Boolean> {

		private static final Logger LOG = Logging.getLogger(MethodEntry.class.getName());

		public MethodEntry(ProfilingEvent.MethodEntryEvent e) {
			super(e);
		}

		@Override
		public Boolean doCall(Writer writer) {
			try {
				event.writeEvent(writer);
				return true;
			} catch (Throwable e) {
				Logging.exception(LOG, e, "Could not write entry event!");
				return false;
			}
		}
	}

	public static class MethodRegister extends ProfilingTask<Boolean> {

		public MethodRegister(ProfilingEvent.MethodRegisterEvent e) {
			super(e);
		}

		@Override
		public Boolean doCall(Writer writer) {
			try {
				event.writeEvent(writer);
				return true;
			} catch (IOException e) {
				return false;
			}
		}
	}

	public static class SystemMethodRegister extends ProfilingTask<Boolean> {

		public SystemMethodRegister(ProfilingEvent.SystemMethodRegisterEvent e) {
			super(e);
		}

		@Override
		public Boolean doCall(Writer writer) {
			try {
				event.writeEvent(writer);
				return true;
			} catch (IOException e) {
				return false;
			}
		}
	}

	public static class BlockEntry extends ProfilingTask<Boolean> {

		public BlockEntry(ProfilingEvent.LoopEntryEvent e) {
			super(e);
		}

		@Override
		public Boolean doCall(Writer writer) {
			try {
				ProfilingEvent.LoopEntryEvent e = (ProfilingEvent.LoopEntryEvent) event;
				// only write useful events...
				if (e.getMethodId() != 0 && e.getSystemMethod() != 0 && e.getIterationCount() > 0) {
					event.writeEvent(writer);
				}
				return true;
			} catch (IOException e) {
				return false;
			}
		}
	}
}
