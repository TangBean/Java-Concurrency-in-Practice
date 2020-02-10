# Java 线程

<!-- TOC -->
<!--
- [Java线程](#java线程)
    - [线程的实现](#线程的实现)
        - [实现多线程的三种方式](#实现多线程的三种方式)
        - [线程的描述](#线程的描述)
        - [实现线程的 3 种方式](#实现线程的-3-种方式)
    - [线程的调度](#线程的调度)
    - [线程的状态转换](#线程的状态转换)
    - [线程间的通信](#线程间的通信)
-->
<!-- /TOC -->

[toc]

- 线程是比进程更轻量级的调度执行单位，CPU 调度的基本单位就是线程。
- 线程的引入，将一个进程的资源分配和执行调度分开。
- 各个线程既可以共享进程资源（内存地址、文件 I/O 等），又可独立调度。
- **Java 的 Thread 类：** 所有关键方法都是 Native 的，说明这些方法无法使用平台无关的手段实现。


## 线程的生命周期
### 通用的线程生命周期

### Java 中线程的生命周期

各种状态之间的转换（画图）


## 线程的生命周期状态转换
### 可运行/运行状态 to 休眠状态

#### RUNNABLE to BLOCKED

#### RUNNABLE to WAITING

#### RUNNABLE to TIMED_WAITING


### 初始状态 to 可运行/运行状态
NEW to RUNNABLE


### 可运行/运行状态 to 中止状态
RUNNABLE to TERMINATED


## 如何在 Java 中使用多线程
### 继承 Thread 类

### 实现 Runnable 接口

### 实现 Callable 接口

## 线程的调度
- **协同式线程调度：** 线程的执行时间由线程本身来控制，线程执行完自己的任务之后，主动通知系统切换到另一个线程。
  - 优点：实现简单，没有线程同步的问题。
  - 缺点：线程执行时间不可控，如果一个线程编写有问题一直无法结束，程序会一直阻塞在那里。
- **抢占式线程调度：** 每个线程由系统分配执行时间，系统决定切不切换线程。

## 线程间的通信

- synchronized 和 volatile 关键字
  - 这两个关键字可以保障线程对变量访问的可见性
- 等待/通知机制
  - 详见 `Ch3-Java并发高级主题/00-Java中的锁.md`
- `Thread#join()`
  - 如果一个线程 A 执行了 `threadA.join()`，那么只有当线程 A 执行完之后，`threadA.join()` 之后的语句才会继续执行，类似于创建 A 的线程要等待 A 执行完后才继续执行；
  - 使用 join 方法中线程被中断的效果 == 使用 wait 方法中线程被中断的效果，即会抛出 InterruptedException。因为 join 方法内部就是用 wait 方法实现的；
  - join 还有一个带参数的方法：`join(long)`，这个方法是等待传入的参数的毫秒数，如果计时过程中等待的方法执行完了，就接着往下执行，如果计时结束等待的方法还没有执行完，就不再继续等待，而是往下执行。
    - **`join(long)` 和 `sleep(long)` 的区别**
      - 如果等待的方法提前结束，`join(long)` 不会再计时了，而是往下执行，而 `sleep(long)` 一定要等待够足够的毫秒数；
      - `join(long)` 会释放锁，`sleep(long)` 不会释放锁，原因是 `join(long)` 方法内部是用 `wait(long)` 方法实现的。
- 管道流：`PipedInputStream` & `PipedOutputStream`

	```java
	public class PipedStreamDemo {
	    public static PipedInputStream in = new PipedInputStream();
	    public static PipedOutputStream out = new PipedOutputStream();
	
	    public static void send() {
	        new Thread() {
	            @Override
	            public void run() {
	                byte[] bytes = new byte[2000];
	                while (true) {
	                    try {
	                        out.write(bytes, 0, 2000);
	                        System.out.println("Send Success");
	                    } catch (IOException e) {
	                        System.out.println("Send Failed");
	                        e.printStackTrace();
	                    }
	                }
	            }
	        }.start();
	    }
	
	    public static void receive() {
	        new Thread() {
	            @Override
	            public void run() {
	                byte[] bytes = new byte[100];
	                int len = 0;
	                while (true) {
	                    try {
	                        len = in.read(bytes, 0, 100);
	                        System.out.println("len = " + len);
	                    } catch (IOException e) {
	                        System.out.println("Receive Failed");
	                        e.printStackTrace();
	                    }
	                }
	            }
	        }.start();
	    }
	
	    public static void main(String[] args) {
	        try {
	            in.connect(out);
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	        receive();
	        send();
	    }
	}
	```

- ThreadLocal
  - 详见 `Ch1-保证线程安全的两个角度/02-对象的安全共享.md`


## 线程的实现原理
### 使用内核线程实现

### 使用用户线程实现

### 使用用户线程加轻量级进程



## 线程数量配置

使用多线程是为了提高性能，这里的
延迟和吞吐量，
我们的目标是降低延迟，提高吞吐量。

- 延迟：发出请求到收到响应这个过程的时间。
- 吞吐量：单位时间内能处理请求的数量。

为了降低延迟，提高吞吐量，一般有以下两种常用手段：
- 优化算法；
- 将硬件的性能发挥到极致。

在并发编程领域，提升性能本质上就是提升硬件的利用率，再具体点来说，就是提升 I/O 的利用率和 CPU 的利用率。我们的目标是让这两个硬件设备同时工作，而不是一个工作的时候另一个歇着。

也就是说，需要解决 CPU 和 I/O 设备综合利用率的问题。

我们的目标是让 CPU 时刻保持着 100% 的利用率，一刻也不停歇的工作着！

而 CPU 密集型的任务和 I/O 密集型的任务有着本质的区别：
- CPU 密集型的任务：大多数时间里，只要在运行就有产出，因此希望一个任务一直运行到底再运行下一个，而不是将时间耗费到线程的切换上（即上下文切换）。
- I/O 密集型的任务：一个任务从开始到完成的时间可能很长，但其间真正在干活（使用 CPU）的时间可能很短，大部分时间都在等待，如等待网络发来的数据包，或等待写入或读取磁盘上的数据等，因此希望在没有产出的等待时间里，CPU 不是闲呆着，而是去做其他的事情。

那么创建多少线程合适呢？

对于 CPU 密集型的计算场景：最佳线程数 = CPU 核数 + 1，后面的 “+1” 是为了一旦线程因为偶尔的内存页失效或其他原因导致阻塞时，这个额外的线程可以顶上，以保证 CPU 的利用率。

对于 I/O 密集型的计算场景：最佳线程数 =CPU 核数 * [ 1 +（I/O 耗时 / CPU 耗时）]。

如 CPU 计算和 I/O 操作的耗时是 1:2，那多少个线程合适呢？是 3 个线程

理想情况下，CPU 在线程 A、B、C 之间按如下方式进行切换，理论上可以实现 100% 的 CPU 利用率（当然这个得是超级理想了，现实中是基本不可能的）。


既然我们要解决的是 CPU 和 I/O 设备的综合利用率问题

需要考虑

线程数量配置的公式只是一个参考，Java 的老版本对于 Docker 容器的支持还不是那么的好，比如有时程序员在 Docker 容器中直接使用 `Runtime.getRuntime().availableProcessors() * 2` 配置线程池大小，对于早期版本的 JVM，这个 `Runtime.getRuntime().availableProcessors()` 会忽略 cgroup 的限制，返回实际物理机的 CPU 数，而当同一物理机上的有好多容器都进行了这样的线程池大小设置操作时，有一下子开启好多线程的风险，可能会导致物理机的崩溃。


