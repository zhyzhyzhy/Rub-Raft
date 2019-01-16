# simple-raft
simple raft  

随时弃坑。。。

# 使用
暂时不支持自定义底层存储  
内置内存的一个Map  

默认端口从5283开始  
日志默认在`/var/log/raft`    
`Rpc端口+1`是一个默认的http服务节点    
`/status`里有所有的路由  

嫌麻烦的话，直接改NodeElectionTest中的一个Test，用Scanner卡住    

GC建议使用G1   
可能CMS的FGC过长会触发重新选举，出现我还没搞懂的Bug😓


# TODO
- 增加Log复制的Test
- 支持节点变更
- 把StateMachine抽象出去
- 完善基于RandomAccessFile的底层文件

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

## InstallSnapShot时间过长 //TODO
不说了，本来就实现的不对  
分离StateMachine和LogService，让StateMachine自己异步的Apply  

## 节点Down了之后重启，ReplicatedLog的response中增加自己的LogIndex //todo
不然每次的LogEntries会非常大

## 针对 Bug => Leader发送重复PrevIndex的List<LogEntry>
可能是需要被覆盖的LogEntry，不能直接丢掉，要set



 
