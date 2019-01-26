# 对比 HashMap & HashTable & TreeMap

[TOC]

## 基本区别

它们都是最常见的 Map 实现，是以键值对的形式存储数据的容器类型。

- HashTable
  - 线程安全，不支持 null 作为键或值，它的线程安全是通过在所有方法 public 方法上加 synchronized 实现的，所以性能很差，很少使用。
- HashMap
	- 不是线程安全的，但是支持 null 作为键或值，是绝大部分利用键值对存取场景的首选，put 和 get 基本可以达到常数级别的时间复杂度。
- TreeMap
	- 基于红黑是的一种提供顺序访问的 Map，它的 get，put，remove 等操作是 O(log(n)) 级别的时间复杂度的（因为要保证顺序），具体的排序规则可以由 Comparator 指定：`public TreeMap(Comparator<? super K> comparator)`。

它们的简单类图如下：

![Map简单类图.jpg](./Pic/Map简单类图.jpg)

在对 Map 的顺序没有要求的情况下，HashMap 基本是最好的选择，不过 HashMap 的性能十分依赖于 hashCode 的有效性，所以必须满足：

- equals 判断相等的对象的 hashCode 一定相等
- 重写了 hashCode 必须重写 equals

我们注意到，除了 TreeMap，LinkedHashMap 也可以保证某种顺序，它们的 **区别** 如下：

- LinkedHashMap：提供的遍历顺序符合插入顺序，是通过为 HashEntry 维护一个双向链表实现的。
- TreeMap：顺序由键的顺序决定，依赖于 Comparator。



## HashMap 源码分析

### HashMap 内部结构

HashMap 的内部结构如下：

![HashMap内部结构.jpg](./Pic/HashMap内部结构.jpg)

> 解决哈希冲突的常用方法：
>
> - 开放地址法：出现冲突时，以当前哈希值为基础，产生另一个哈希值。
> - 再哈希法：同时构造多个不同的哈希函数，发生冲突就换一个哈希方法。
> - 链地址法：将哈希地址相同的元素放在一个链表中，然后把这个链表的表头放在哈希表的对应位置。
> - 建立公共溢出区：将哈希表分为基本表和溢出表两部分，凡是和基本表发生冲突的元素，一律填入溢出表。

HashMap 采用的是链表地址法，不过如果由一个位置的链表比较长了（超过阈值 8 了），链表会被改造为树形结构以提高查找性能。

这个桶数组并没有在 HashMap 的构造函数中初始化好，只是设置了容量，应该是采用了 lazy-load 原则。

```java
public HashMap(int initialCapacity, float loadFactor){  
    // ... 
    this.loadFactor = loadFactor;
    this.threshold = tableSizeFor(initialCapacity);
}
```

接下来，我们看一下 put 方法：

```java
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}
```

可以看到，put 方法调用了 putVal 方法：

```java
final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
               boolean evict) {
    Node<K,V>[] tab; Node<K,V> p; int n, i;
    // lazy-load，tab要是空的，用resize初始化它
    // resize既要负责初始化，又要负责在容量不够时扩容
    if ((tab = table) == null || (n = tab.length) == 0)
        n = (tab = resize()).length;
    // 无哈希冲突，直接new一个节点放到tab[i]就行
    // 具体键值对在哈希表中的位置：i = (n - 1) & hash
    if ((p = tab[i = (n - 1) & hash]) == null)
        tab[i] = newNode(hash, key, value, null);
    else {
        Node<K,V> e; K k;
        // 该key存在，直接修改value就行
        if (p.hash == hash &&
            ((k = p.key) == key || (key != null && key.equals(k))))
            e = p;
        // 当前hashCode下面挂的已经是颗树了，用树的插入方式插入新节点
        else if (p instanceof TreeNode)
            e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
        // 当前hashCode下面挂的还是个链表，不过保不齐会变成颗树
        else {
            // ...
            if (binCount >= TREEIFY_THRESHOLD - 1) // 链表要变树啦！
                treeifyBin(tab, hash);
            // ...
        }
    }
    ++modCount;
    if (++size > threshold) // 容量不够了，扩容
        resize();
}
```

**分析：**

- key 的 hashCode 用的并不是 key 自己的 hashCode，而是通过 HashMap 内部的一个 hash 方法另算的。那么为什么要另算一个 hashCode 呢？这是因为： **有些数据计算出的哈希值差异主要在高位，而 HashMap 里的哈希寻址是忽略容量以上的高位的，这种处理可以有效避免这种情况下的哈希碰撞。**

	```java
	static final int hash(Object key) {
	    int h;
	    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
	}
	```

- resize 方法：

	```java
	final Node<K,V>[] resize() {
	    Node<K,V>[] oldTab = table;
	    int oldCap = (oldTab == null) ? 0 : oldTab.length;
	    int oldThr = threshold;
	    int newCap, newThr = 0;
	    if (oldCap > 0) {
	        // MAXIMUM_CAPACITY = 1 << 30，如果超过这个容量就扩不了容了
	        if (oldCap >= MAXIMUM_CAPACITY) {
	            threshold = Integer.MAX_VALUE;
	            return oldTab;
	        }
	        // newCap = oldCap << 1，容量变成原来的2倍
	        else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
	                 oldCap >= DEFAULT_INITIAL_CAPACITY)
	            newThr = oldThr << 1; // double threshold
	    }
	    else if (oldThr > 0) // initial capacity was placed in threshold
	        newCap = oldThr;
	    else {               // zero initial threshold signifies using defaults
	        newCap = DEFAULT_INITIAL_CAPACITY;
	        newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
	    }
	    if (newThr == 0) {
	        float ft = (float)newCap * loadFactor;
	        newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
	                  (int)ft : Integer.MAX_VALUE);
	    }
	    threshold = newThr;
	    @SuppressWarnings({"rawtypes","unchecked"})
	    Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
	    table = newTab;
	    // 把oldTab中的数据移到newTab中，这里是要进行rehash的
	    if (oldTab != null) {
	        for (int j = 0; j < oldCap; ++j) {
	            Node<K,V> e;
	            if ((e = oldTab[j]) != null) {
	                oldTab[j] = null;
	                if (e.next == null)
	                    newTab[e.hash & (newCap - 1)] = e;
	                else if (e instanceof TreeNode)
	                    ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
	                else { // preserve order
	                    Node<K,V> loHead = null, loTail = null;
	                    Node<K,V> hiHead = null, hiTail = null;
	                    Node<K,V> next;
	                    do {
	                        next = e.next;
	                        if ((e.hash & oldCap) == 0) {
	                            if (loTail == null)
	                                loHead = e;
	                            else
	                                loTail.next = e;
	                            loTail = e;
	                        }
	                        else {
	                            if (hiTail == null)
	                                hiHead = e;
	                            else
	                                hiTail.next = e;
	                            hiTail = e;
	                        }
	                    } while ((e = next) != null);
	                    if (loTail != null) {
	                        loTail.next = null;
	                        newTab[j] = loHead;
	                    }
	                    if (hiTail != null) {
	                        hiTail.next = null;
	                        newTab[j + oldCap] = hiHead;
	                    }
	                }
	            }
	        }
	    }
	    return newTab;
	}
	```



### 容量、负载因子和树化

容量和负载因子决定了桶数组中的桶数量，如果桶太多了会浪费空间，但桶太少又会影响性能。我们要保证：

```
负载因子 * 容量 > 元素数量 && 容量要是 2 的倍数
```

对于负载因子：

- 如果没有特别需求，不要轻易更改；
- 如果需要调整，不要超过 0.75，否则会显著增加冲突；
- 如果使用太小的负载因子，也要同时调整容量，否则可能会频繁扩容，影响性能。

那么为什么要树化呢？

这本质上是一个安全问题，我们知道如果同一个哈希值对应位置的链表太长，会极大的影响性能，而在现实世界中，构造哈希冲突的数据并不是十分复杂的事情，恶意代码可以利用这些数据与服务端进行交互，会导致服务端 CPU 大量占用，形成哈希碰撞拒绝服务攻击。

