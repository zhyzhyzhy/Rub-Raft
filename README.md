# simple-raft
simple raft

# TODO
- ~~RpcClient和RpcServer自动重连~~
- 增加Log复制的Test
- ~~增加SnapShot的支持~~
- 支持节点变更
- 把StateMachine抽象出去
- 完善基于RandomAccessFile的底层文件
- update LogService For Corner Case

# BUG记录
## 选举
发送Vote请求后，利用CountDownLatch把主线程挂住，当超过3个回复，就回来
但是不行，5个节点，收到2个grant就返回，选举成功，相应的，收到3个refuse也返回，但是这个条件是选举失败
这里是两套逻辑

## Leader发送重复PrevIndex的List<LogEntry>
是我弱智了，判断了下PrevIndex的Term，就直接Append了
但是是重复的，所以还要对List<LogEntry>做截断

## 定时调度器被打满
定时调度器被任务打满，运行了一会了有7w加任务  
单JVM进程，5节点，啥事也没干  
都是HeartBeat过来的时候，启动了一个选举超时的任务 
解决方案：没想到什么好的，自己封装一个方法单独用来选举超时
一个节点一个单独的线程

## InstallSnapShot时间过长
不说了，本来就实现的不对
分离StateMachine和LogService，让StateMachine自己异步的Apply

 
