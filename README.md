# Rub-Raft
Rub-Raft 

# 使用
配置`server.properties`  
```
local=localhost:5283:0  //表示当前节点
peer=localhost:5285:1,localhost:5287:2 //表示其他节点
```
使用RaftStarter.start(Properties properties)启动

暂时不支持自定义底层存储  
内置内存的一个Map  

日志默认在`/var/log/raft/*.log`

默认RPC端口从5283开始  
`Rpc端口+1`是一个日志查看节点
`Rpc端口+2`是一个默认的http服务节点    
`/status`里有所有的路由     

# 测试
Mock了6.824的测试样例

# TODO
- ~~支持节点变更~~
- 完善基于RandomAccessFile的底层文件
- 重构代码




 
