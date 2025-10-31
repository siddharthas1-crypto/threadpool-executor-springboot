package com.example.threadpooldemo.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import com.example.threadpooldemo.config.GracefulThreadPoolExecutor;
import java.util.concurrent.*;

/**
 * Unit tests for {@link GracefulThreadPoolExecutor}.
 * 
 * These tests validate correct shutdown behavior, interruption handling, and
 * post-shutdown task rejection, ensuring that thread pools cleanly terminate
 * without leaks or hangs.
 */
public class GracefulThreadPoolExecutorTest {

	private GracefulThreadPoolExecutor newExecutor() {
		return new GracefulThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), Thread::new,
				new ThreadPoolExecutor.AbortPolicy());
	}

	@Test
	public void testGracefulShutdownCompletesNormally() {
		GracefulThreadPoolExecutor exec = newExecutor();
		exec.execute(() -> {
		}); // simple no-op task

		exec.shutdownGracefully();

		Assertions.assertTrue(exec.isShutdown(), "Executor should be shut down");
		Assertions.assertTrue(exec.isTerminated(), "Executor should terminate within timeout");
	}

	@Test
	public void testGracefulShutdownInterruptsLongTasks() {
		GracefulThreadPoolExecutor exec = newExecutor();

		exec.execute(() -> {
			try {
				Thread.sleep(10000); // simulate long-running task
			} catch (InterruptedException ignored) {
			}
		});

		long start = System.currentTimeMillis();
		exec.shutdownGracefully();
		long duration = System.currentTimeMillis() - start;

		Assertions.assertTrue(duration < 7000, "Shutdown should not block indefinitely");
		Assertions.assertTrue(exec.isShutdown(), "Executor should be in shutdown state");
	}

	@Test
	public void testDoubleShutdownGracefully() {
		GracefulThreadPoolExecutor exec = newExecutor();
		exec.shutdownGracefully();
		Assertions.assertDoesNotThrow(exec::shutdownGracefully, "Calling shutdownGracefully twice should not throw");
		Assertions.assertTrue(exec.isShutdown());
	}

	@Test
	public void testRejectedExecutionAfterShutdownGracefully() {
		GracefulThreadPoolExecutor exec = newExecutor();
		exec.shutdownGracefully();

		Assertions.assertThrows(RejectedExecutionException.class, () -> exec.execute(() -> {
		}), "Should reject new tasks after shutdown");
	}

	@Test
	public void testGracefulShutdownHandlesInterruptedException() throws Exception {
		GracefulThreadPoolExecutor exec = newExecutor();

		Thread mainThread = Thread.currentThread();
		Thread interrupter = new Thread(() -> {
			try {
				Thread.sleep(50);
				mainThread.interrupt();
			} catch (InterruptedException ignored) {
			}
		});
		interrupter.start();

		Assertions.assertDoesNotThrow(exec::shutdownGracefully,
				"Executor should handle InterruptedException gracefully");
		Assertions.assertTrue(exec.isShutdown(), "Executor should be shut down even after interrupt");
		Thread.interrupted(); // clear interrupt flag
	}

	@Test
	public void testShutdownWaitsForTasks() {
		GracefulThreadPoolExecutor exec = newExecutor();
		final boolean[] completed = { false };

		exec.execute(() -> {
			try {
				Thread.sleep(200);
				completed[0] = true;
			} catch (InterruptedException ignored) {
			}
		});

		exec.shutdownGracefully();
		Assertions.assertTrue(completed[0], "Task should complete before termination");
		Assertions.assertTrue(exec.isTerminated(), "Executor should be terminated");
	}

}
