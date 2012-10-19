package me.ferry.bukkit.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 *An abstract class to perform lengthy Server-interacting tasks in a
 * dedicated thread.
 * 
 * <p>
 * When writing a multi-threaded application using bukkit, there are
 * two constraints to keep in mind:
 * <ul>
 *   <li> Time-consuming tasks should not be run on the <i>Bukkit Main Server Thread</i>. Otherwise the server becomes unresponsive.
 *   </li>
 *   <li> Bukkit components should be accessed  on the <i>Bukkit Main Server Thread</i> only.
 *   </li>
 * </ul>
 * Note: the done methodes may not been called if the plujgin that is running this instance is stopped/disabled
 *
 * @param <T> the result type returned by this {@code BukkitWorker's} {@code doInBackground} and {@code get} methods
 * @param <V> <V> the type used for carrying out intermediate results by this {@code BukkitWorker's} {@code publish} and {@code process} methods
 * @param <P> Plugin class
 * @author Ferrybig
 */
public abstract class BukkitWorker<T, V, P extends Plugin> implements RunnableFuture<T>
{
	private final AccumulativeRunnable<Runnable> doSubmit;
	/**
	 * current state.
	 */
	private volatile StateValue state = StateValue.PENDING;
	/**
	 * everything is run inside this FutureTask. Also it is used as
	 * a delegatee for the Future API.
	 */
	private final FutureTask<T> future;
	/**
	 * The plugin that is running this, because its using generics for this field, you dont need to add your own field for the plugin instace
	 */
	protected final P plugin;
	private AccumulativeRunnable<V> doProcess;

	/**
	 * Creates a new BukkitWorker object
	 * @param plugin The plugin that is the host of this {@code BukkitWorker}
	 */
	public BukkitWorker(P plugin)
	{
		this.future = new FutureTask<T>(new Callable<T>()
		{
			@Override
			public T call() throws Exception
			{
				BukkitWorker.this.setState(StateValue.STARTED);
				return BukkitWorker.this.doInBackground();
			}
		})
		{
			@Override
			protected void done()
			{
				BukkitWorker.this.doneTask();
				BukkitWorker.this.setState(StateValue.DONE);
			}
		};
		this.doSubmit = new DoSubmitAccumulativeRunnable(plugin);
		this.plugin = plugin;
	}

	/**
	 * Computes a result, or throws an exception if unable to do so.
	 *
	 * <p>
	 * Note that this method is executed only once.
	 * 
	 * <p>
	 * Note: this method is executed in a background thread.
	 *  
	 *
	 * @return the computed result
	 * @throws Exception if unable to compute a result
	 * 
	 */
	protected abstract T doInBackground() throws Exception;

	/**
	 * Executed on the <i>Bukkit Main Server Thread</i> after the {@code doInBackground}
	 * method is finished. The default
	 * implementation does nothing. Subclasses may override this method to
	 * perform completion actions on the <i>Bukkit Main Server Thread</i>. Note
	 * that you can query status inside the implementation of this method to
	 * determine the result of this task or whether this task has been cancelled.
	 * 
	 * @see #doInBackground
	 * @see #isCancelled()
	 * @see #get
	 */
	protected void done()
	{
	}

	/**
	 * Receives data chunks from the {@code publish} method asynchronously on the
	 * <i>Bukkit Main Server Thread</i>.
	 * 
	 * <p>
	 * Please refer to the {@link #publish} method for more details.
	 * 
	 * @param chunks intermediate results to process
	 * 
	 * @see #publish
	 * 
	 */
	protected void process(List<V> chunks)
	{
	}

	/**
	 * Sets this {@code Future} to the result of computation unless
	 * it has been cancelled.
	 */
	@Override
	public final void run()
	{
		this.future.run();
	}

	/**
	 * Schedules this {@code BukkitWorker} for execution on a <i>worker</i>
	 * thread.
	 *
	 * <p>
	 * Note
	 * {@code BukkitWorker} is only designed to be executed once.  Executing a
	 * {@code BukkitWorker} more than once will not result in invoking the
	 * {@code doInBackground} method twice.
	 * 
	 */
	public final void execute()
	{
		if (this.state == StateValue.PENDING)
		{
			Bukkit.getScheduler().scheduleAsyncDelayedTask(this.plugin, this.future);
		}
	}

	/**
	 * Sends data chunks to the {@link #process} method. This method is to be
	 * used from inside the {@code doInBackground} method to deliver 
	 * intermediate results
	 * for processing on the <i>Bukkit Main Server Thread</i> inside the
	 * {@code process} method.
	 * 
	 * <p>
	 * Because the {@code process} method is invoked asynchronously on
	 * the <i>Bukkit Main Server Thread</i>
	 * multiple invocations to the {@code publish} method
	 * might occur before the {@code process} method is executed. For
	 * performance purposes all these invocations are coalesced into one
	 * invocation with concatenated arguments.
	 * 
	 * <p>
	 * For example:
	 * 
	 * <pre>
	 * publish(&quot;1&quot;);
	 * publish(&quot;2&quot;, &quot;3&quot;);
	 * publish(&quot;4&quot;, &quot;5&quot;, &quot;6&quot;);
	 * </pre>
	 * 
	 * might result in:
	 * 
	 * <pre>
	 * process(&quot;1&quot;, &quot;2&quot;, &quot;3&quot;, &quot;4&quot;, &quot;5&quot;, &quot;6&quot;)
	 * </pre>
	 *
	 * 
	 * @param chunks intermediate results to process
	 * 
	 * @see #process
	 * 
	 */
	protected final void publish(V... chunks)
	{
		synchronized (this)
		{
			if (this.doProcess == null)
			{
				this.doProcess = new AccumulativeRunnable<V>()
				{
					@Override
					public void run(List<V> args)
					{
						BukkitWorker.this.process(args);
					}

					@Override
					protected void submit()
					{
						BukkitWorker.this.doSubmit.add(this);
					}
				};
			}
		}
		this.doProcess.add(chunks);
	}

	/**
	 * Returns the {@code BukkitWorker} current state.
	 * 
	 * @return the current state
	 */
	public final StateValue getState()
	{
		/*
		 * DONE is a speacial case
		 * to keep getState and isDone is sync
		 */
		if (this.isDone())
		{
			return StateValue.DONE;
		}
		else
		{
			return this.state;
		}
	}

	/**
	 * {@inheritDoc}
	 * <br/>
	 * Notice, this methode is called if the plugin is stopping 
	 */
	@Override
	public final boolean cancel(boolean mayInterruptIfRunning)
	{
		return future.cancel(mayInterruptIfRunning);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final boolean isCancelled()
	{
		return this.future.isCancelled();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final boolean isDone()
	{
		return this.future.isDone();
	}

	/**
	 * Sets this {@code BukkitWorker} state bound property.
	 * @param state the state to set
	 */
	private void setState(StateValue state)
	{
		StateValue old = this.state;
		this.state = state;
	}

	/**
	 * Invokes {@code done} on the Bukkit Main Server Thread.
	 */
	private void doneTask()
	{
		this.doSubmit.add(new Runnable()
		{
			@Override
			public void run()
			{
				BukkitWorker.this.done();
			}
		});

	}

	/**
	 * get the plugin that created this {@code BukkitWorker}
	 * @return the plugin
	 */
	public final P getPlugin()
	{
		return this.plugin;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Note: calling {@code get} on the <i>Bukkit Main Server Thread</i> blocks
	 * <i>all</i> other tasks from being processed until this
	 * {@code BukkitWorker} is complete. (This is not recommend to do!)
	 */
	@Override
	public final T get() throws InterruptedException, ExecutionException
	{
		return this.future.get();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Please refer to {@link #get} for more details.
	 */
	@Override
	public final T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
	{
		return this.future.get(timeout, unit);
	}

	private static abstract class AccumulativeRunnable<T extends Object> implements Runnable
	{
		private List<T> arguments = null;

		protected abstract void run(List<T> paramList);

		@Override
		public final void run()
		{
			this.run(this.flush());
		}

		public final synchronized void add(T... toAdd)
		{
			boolean mustSubmit = false;
			if (this.arguments == null)
			{
				mustSubmit = true;
				this.arguments = new ArrayList<T>();
			}
			Collections.addAll(this.arguments, toAdd);
			if (mustSubmit)
			{
				this.submit();
			}
		}

		abstract protected void submit();

		private synchronized List<T> flush()
		{
			List<T> localList = this.arguments;
			this.arguments = null;
			return localList;
		}
	}

	private class DoSubmitAccumulativeRunnable extends AccumulativeRunnable<Runnable> implements Runnable
	{
		/**
		 *  Time in ticks between 2 invokings of the schedular
		 */
		private final static int DELAY = 2;
		/**
		 * The plugin, used to schedule tasks
		 */
		private final Plugin plugin;

		public DoSubmitAccumulativeRunnable(Plugin plugin)
		{
			this.plugin = plugin;
		}

		@Override
		protected void run(List<Runnable> args)
		{
			for (Runnable runnable : args)
			{
				runnable.run();
			}
		}

		@Override
		protected void submit()
		{
			if (this.plugin.isEnabled())
			{
				Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, this, DELAY);
			}
			else
			{
				// What do do, scheduling an task would throw an IllegalArgumentException, and enablking this plugin for a little time may cause bugs inside the plugin?
				// Mayby use reflection to access the enabled field of JavaPlugin, but this wont work whit plugins that dont exend that class
			}
		}
	}

	/**
	 * Values for the {@link #getState() } methode.
	 */
	public enum StateValue
	{
		/**
		 * Initial {@code BukkitWorker} state.
		 */
		PENDING,
		/**
		 * {@code BukkitWorker} is {@code STARTED} 
		 * before invoking {@code doInBackground}.
		 */
		STARTED,
		/**
		 * {@code BukkitWorker} is {@code DONE}
		 * after {@code doInBackground} method
		 * is finished.
		 */
		DONE
	};
}
