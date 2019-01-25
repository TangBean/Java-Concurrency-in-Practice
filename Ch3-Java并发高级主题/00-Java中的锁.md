# Java 中的锁

<!-- TOC -->

- [Java中的锁](#java%E4%B8%AD%E7%9A%84%E9%94%81)
	- [Lock接口](#lock%E6%8E%A5%E5%8F%A3)
		- [`Lock` 的标准使用示例](#lock-%E7%9A%84%E6%A0%87%E5%87%86%E4%BD%BF%E7%94%A8%E7%A4%BA%E4%BE%8B)
		- [`Lock` 的 API](#lock-%E7%9A%84-api)
	- [3 个高级的 lock 方法](#3-%E4%B8%AA%E9%AB%98%E7%BA%A7%E7%9A%84-lock-%E6%96%B9%E6%B3%95)
			- [轮询锁： `tryLock()`](#%E8%BD%AE%E8%AF%A2%E9%94%81-trylock)
			- [定时锁： `tryLock(long time, TimeUnit unit)`](#%E5%AE%9A%E6%97%B6%E9%94%81-trylocklong-time-timeunit-unit)
			- [中断锁： `lockInterruptibly()`](#%E4%B8%AD%E6%96%AD%E9%94%81-lockinterruptibly)
	- [公平锁与非公平锁： `ReentrantLock(boolean fair)`](#%E5%85%AC%E5%B9%B3%E9%94%81%E4%B8%8E%E9%9D%9E%E5%85%AC%E5%B9%B3%E9%94%81-reentrantlockboolean-fair)
	- [Condition： `newCondition()`](#condition-newcondition)
		- [等待/通知机制](#%E7%AD%89%E5%BE%85%E9%80%9A%E7%9F%A5%E6%9C%BA%E5%88%B6)
		- [Condition 接口](#condition-%E6%8E%A5%E5%8F%A3)
	- [synchronized 和 ReentrantLock 的选择](#synchronized-%E5%92%8C-reentrantlock-%E7%9A%84%E9%80%89%E6%8B%A9)
	- [读写锁：ReentrantReadWriteLock](#%E8%AF%BB%E5%86%99%E9%94%81reentrantreadwritelock)

<!-- /TOC -->

## Lock 接口

在 Java SE 5 后，Java 并发包 java.util.concurrent 中新增了 `Lock` 接口及其相关实现类，如：`ReentrantLock` 来实现锁的功能，它提供了与 `synchronized` 相似的同步功能，不过在使用时需要显示的获取锁和释放锁，虽然从这个角度来看，使用 Lock 接口更为麻烦，不过我们可以通过 Lock 接口的实现类，实现以下功能：

- 尝试非阻塞的获取锁，即 `tryLock()`
  - `tryLock()` 方法会尝试非阻塞的获取锁，即调用方法后不管能否取到锁都会立即返回，不会被阻塞
- 能被中断的获取锁
  - 与 synchronied 不同，获取到锁的线程能相应中断，当线程被中断时，中断异常会被抛出，并且锁也会被释放
- 超时获取锁，即 `tryLock(long time, TimeUnit unit)`


### `Lock` 的标准使用示例

```java
Lock lock = new ReentrantLock();
lock.lock();
try {
    // 同步代码块
} finally {
    lock.unlock(); // 千万不能忘记在finally块中释放锁
}
```

### `Lock` 的 API

```java
/* 构造方法 */
public ReentrantLock(boolean fair) { // fair默认是false
    sync = fair ? new FairSync() : new NonfairSync();
}

/* 重要方法 */
void lock()
void lockInterruptibly() throws InterruptedException
boolean tryLock()
boolean tryLock(long time, TimeUnit unit) throws InterruptedException
void unlock()
Condition newCondition()
```

接下来，我们将对 Lock 的重要方法进行介绍。

## 3 个高级的 lock 方法

#### 轮询锁： `tryLock()`

- 只有在锁没有被其他线程拿到时才获取锁，然后返回 true，否则返回 false，会立即返回，**不会阻塞**

- 不是可中断锁

- **可以避免锁顺序死锁的发生**

	- 我们知道，死锁发生的一个典型示例就是锁顺序死锁，即（假设我们要进行一个转账操作）

		```java
		public boolean transferMoney(Account fromAcct, Account toAcct, double money) {
		    synchronized (fromAcct) {
		        synchronized (toAcct) {
					// 转账
		        }
		    }
		}
		
		// 调用
		final Account A = new Account();
		final Account B = new Account();
		new Thread() {
		    public void run() {
		        transferMoney(A, B, 100)
		    }
		}.start();
		
		new Thread() {
		    public void run() {
		        transferMoney(B, A, 100)
		    }
		}.start();
		// 两个线程在进行方向相反的转账操作，及容易发生死锁！
		```

	- 我们可以通过 `tryLock()` 的方式来避免锁顺序死锁

		```java
		public boolean transferMoney(Account fromAcct, Account toAcct, double money) {
		    long fixedDelay = getFixedDelayComponentNanos(timeout, unit); // 固定时延部分
		    long randMod = getRandomDelayModulusNanos(timeout, unit); // 随机时延部分
		    long stopTime = System.nanoTime() + unit.toNanos(timeout); // 过期时间
		    while (true) {
		        if (fromAcct.lock.tryLock()) {
		            try {
		                if (toAcct.lock.tryLock()) { // 如果失败了，该线程会放开已经持有的锁，避免了死锁发生
		                    try {
		                        // 转账
		                    } finally {
		                        toAcct.lock.unlock();
		                    }
		                }
		            } finally {
		                fromAcct.lock.unlock();
		            }
		        }
		        if (System.nanoTime() < stopTime) // 检查是否超时
		            return false;
		        NANOSECONDS.sleep(fixedDelay + rnd.nextLong() % randMod); // 等待一定时长，防止陷入活锁
		    }
		}
		```

#### 定时锁： `tryLock(long time, TimeUnit unit)`

- 定时锁是可中断锁，你看它是能 `throw InterruptedException` 的，能抛出 InterruptedException 的方法都是阻塞方法
- 等待 timeout 时间，再去 tryLock 锁

#### 中断锁： `lockInterruptibly()`

- 能在获得锁的同时保持对中断的响应，即在调用 lockInterruptibly() 获得锁之后，如果线程被 interrupt() 打上了中断标记，会抛中断异常
- 相当于在同步代码块中加入了取消点

## 公平锁与非公平锁： `ReentrantLock(boolean fair)`

- **公平锁：** 在有线程持有锁和有线程在队列中等待锁的时候，会将新发出请求的线程放入队列中，而不是立即执行它，也就是说，获取锁的顺序和线程请求锁的顺序是一样的。
- **非公平锁：** 只当有线程持有锁时，新发出请求的线程才被放入队列中，如果新的线程到达时没有线程持有锁，但队列中有等待的线程（比如队列中的线程还在启动中，还没有拿到锁），这时新请求锁的线程会先于队列中的线程获取锁。
- **非公平锁性能更优的原因：**
	- 恢复一个被挂起的线程到这个线程真正运行起来之间，存在着巨大时时延
	- 在等待队列中的线程被恢复的超长时延里，如果正好进来了一个线程获取锁，非公平锁会让这个新进来的线程先执行，它很有可以能等待队列中的线程恢复运行前就执行完了，相当于时间不变的情况下，利用等待线程的恢复运行时延，多执行了一个线程
	- 只要当线程运行时间长，或锁的请求频率比较低时，才适合使用公平锁

## Condition： `newCondition()`

在介绍 Condition 前，我们要先来介绍以下为什么需要 Condition，因此，我们需要先来介绍一下 “等待/通知机制”。

### 等待/通知机制

**主要方法：** 这些方法都是 Object 类的方法，因为 synchronized 可以将任意一个对象作为锁。

```java
wait()             // 使调用该方法的线程释放锁，从运行状态中退出，进入等待队列，直到接到通知或者被中断
wait(long timeout) // 等待time毫秒内是否有线程对其进行了唤醒，如果超过这个时间则自动唤醒
notify()           // 随机唤醒等待队列中等待锁的一个线程，使该线程退出等待队列，进入可运行状态
notifyAll()        // 使所有线程退出等待队列，进入可运行状态，执行的顺序由JVM决定
```

> 注意
>
> - 在调用以上这些方法时，如果它们没有持有适当的锁，即不在同步代码块中，会抛出 IllegalMonitorStateException 异常（RuntimeException，不用 catch），同时调用 wait() 和 notify() 的方法也必须是同一个对象
> - 最好使用 notifyAll() 来唤醒等待线程，不然很容易发生死锁

**wait 的线程是如何被其对应的 notify 通知到的？（等待/通知机制实现原理）！！！**

- 每个锁对象都又两个队列，一个是就绪队列，一个是阻塞队列
- 就绪队列中存储了将要获得锁的线程，阻塞队列中存储了被阻塞的线程
- 一个线程被唤醒后，会进入就绪队列，等待 CPU 调度
- 一个线程被 wait 后就会进入阻塞队列，等待其他线程调用 notify，它才会被选中进入就绪队列，等待被 CPU 调度

**条件队列的标准使用形式：**

```java
void stateDependentMethod() throws InterruptedException {
    synchronized (lock) {
        while(!conditionPredicate())
            lock.wait(); // 一个条件队列可能与多个条件相关，
                         // 我们并不知道notifyAll是针对哪一个条件的，
                         // 为了防止wait被过早唤醒，wait必须放在循环中！
    }
}

void stateAwakeMethod() {
    synchronized (lock) {
        lock.notifyAll(); // 不要使用notify！！！
                          // 一旦有一个notify错误的在wait前执行了，
                          // 将会有一个wait永远无法被唤醒！
    }
}
```

**使用 wait 和 notifyAll 实现可重新关闭的阀门：巧妙的用法！！！**

```java
public class ThreadGate {
    @GuardedBy("this") private boolean isOpen;
    @GuardedBy("this") private int generation;

    public synchronized void close() {
        isOpen = false;
    }

    public synchronized void open() {
        ++generation;
        isOpen = true;
        notifyAll();
    }
    
    public synchronized void await() throws InterruptedException {
        int arrivalGeneration = generation;
        // 如果阀门打开后很快就关闭了，那么这个while循环可能检测不到isOpen为true的状态，
        // 会一直阻塞在这里；添加一个generation，在open时该变它的值，
        // 这样只要open了一次，这个while循环就一直为false了，一定会放行线程！
        while (!isOpen && arrivalGeneration == generation)
            wait();
    }
}
```

通过观察上面：条件队列的标准使用形式，我们发现 “等待/通知机制” 由于不能指定条件，使用起来是很不方便的，因为我们不能控制 notify 唤醒的 wait 到底是哪一个，可能会导致：提前唤醒了正在 wait 的线程，然后本应该被唤醒的 wait 却没有被唤醒。这种时候，我们可以通过 `Condition` 来实现 wait 和 notify 的分堆，防止 notify 唤醒别人的 wait。

### Condition 接口

```java
/* 获取Condition的方法 */
protected final Lock lock = new ReentrantLock();
Condition condition = lock.newCondition();

/* Condition接口中的方法 */
void await() throws InterruptedException; // 相当于wait()
void awaitUninterruptibly();
long awaitNanos(long nanosTimeout) throws InterruptedException;
boolean await(long time, TimeUnit unit) throws InterruptedException; // 相当于wait(long timeout)
boolean awaitUntil(Date deadline) throws InterruptedException;
void signal(); // 相当于notify()
void signalAll(); // 相当于notifyAll()
```

有了 Condition 后，我们就可以选择使用 `signal()` 而不是 `signalAll()` 了，因为我们不用担心 `signal()` 会唤醒其他 `await()` 然后错过自己本该唤醒的 `await()` 了。这个时候使用 `signal()`，每次只会唤醒一个线程，能降低锁的竞争，减少上下问切换的次数，性能是要比 `signalAll()` 好的。

## synchronized 和 ReentrantLock 的选择

- **选择方式：**
	- 只有当我们需要如下高级功能时才使用 ReentrantLock，否则优先使用 synchronized
		- 可轮询、可定时、可中断的锁
		- 公平锁
		- 非块结构的锁
- **优先选择 synchronized 的原因：**
	- Java 6开始，ReenstrantLock 和内置锁的性能相差不大
	- synchronized 是 JVM 的内置属性，未来更有可能对 synchronized 进行性能优化，如对线程封闭的锁对象的锁消除，增加锁的粒度等
	- ReenstrantLock 危险性更高（如忘记在 finally 块中 lock.unlock() 了，会导致锁永远无法被释放，出现问题，极难 debug）
	- 许多现有程序中已使用了 synchronized，两种方式混合使用比较易错

## 读写锁：ReentrantReadWriteLock

**特点：** 支持读操作并发执行，涉及到写操作时才线程间互斥执行。

**方法：**

- 获得读锁： `lock.readLock().lock()`
- 释放读锁： `lock.readLock().unlock()`
- 获得写锁： `lock.writeLock().lock()`
- 释放写锁： `lock.writeLock().unlock()`