# AbstractQueuedSynchronizer 框架 (AQS)

<!-- TOC -->

- [AbstractQueuedSynchronizer 框架 (AQS)](#abstractqueuedsynchronizer-%E6%A1%86%E6%9E%B6-aqs)
	- [AQS 框架的方法](#aqs-%E6%A1%86%E6%9E%B6%E7%9A%84%E6%96%B9%E6%B3%95)
	- [AQS 使用方法](#aqs-%E4%BD%BF%E7%94%A8%E6%96%B9%E6%B3%95)

<!-- /TOC -->


AQS 框架是用来构建锁或其他同步组件的基础框架，其核心思想为： **如果被请求的共享资源空闲，则将当前请求资源的线程设置为有效的工作线程，并且将共享资源设置为锁定状态。如果被请求的共享资源被占用，那么就需要一套线程阻塞等待以及被唤醒时锁分配的机制，AQS 通过 CLH 队列实现了这种机制。** 其实现原理为： **使用了一个 int 成员变量表示同步状态，然后通过内置的 FIFO 队列来完场资源获取线程的排队工作**  。使用 AQS 能简单高效地构造出大量的同步器，如：

- ReentrantLock
- Semaphore
- CountDownLatch
- FutureTask
- ReentrantReadWriteLock

> CLH (Craig,Landin,and Hagersten) 队列是一个虚拟的双向队列（虚拟的双向队列即不存在队列实例，仅存在结点之间的关联关系）。AQS 是将每条请求共享资源的线程封装成一个 CLH 锁队列的一个结点（Node）实现锁分配的，并且这个队列遵循 FIFO 原则。



## AQS 框架的方法

在构建同步器的过程中，我们主要依赖于一下几类操作：

- **状态更改操作：**
	- `protected final int getState()`
	- `protected final void setState(int newState)`
	- `protected final boolean compareAndSetState(int expect, int update)`
- **获取和释放操作：**
	- 独占式：
		- `public final void acquire(int arg)`
		- `public final boolean release(int arg)`
	- 共享式：
		- `public final void acquireShared(int arg)`
		- `public final boolean releaseShared(int arg)`
- **try 获取和释放操作：** 模板方法，extends AbstractQueuedSynchronizer 时需要按需修改的方法。
	- 独占式：
		- `protected boolean tryAcquire(int arg)`
		- `protected boolean tryRelease(int arg)`
	- 共享式：
		- `protected int tryAcquireShared(int arg)`
		- `protected boolean tryReleaseShared(int arg)`
- **判断同步器是否被当前线程独占：**
	- `protected boolean isHeldExclusively()`



## AQS 使用方法

- 在要构建的同步类中加一个私有静态内部类：`private class Sync extends AbstractQueuedSynchronizer`

- 在子类中覆盖 AQS 的 try 前缀等方法，这样 Sync 将在执行获取和释放方法时，调用这些被子类覆盖了的 try 方法来判断某个操作是否能执行（模板方法模式，就是基于继承该类，然后根据需要重写模板方法）

- 一个 AQS 实现简单闭锁的示例：

	```java
	public class OneShotLatch {
	    private final Sync sync = new Sync();
	
	    public void signal() {
	        sync.releaseShared(0);
	    }
	
	    public void await() throws InterruptedException {
	        sync.acquireSharedInterruptibly(0);
	    }
	
	    private class Sync extends AbstractQueuedSynchronizer {
	        protected int tryAcquireShared(int ignored) {
	            // Succeed if latch is open (state == 1), else fail
	            return (getState() == 1) ? 1 : -1;
	        }
	        protected boolean tryReleaseShared(int ignored) {
	            setState(1); // Latch is now open
	            return true; // Other threads may now be able to acquire
	        }
	    }
	}
	```



