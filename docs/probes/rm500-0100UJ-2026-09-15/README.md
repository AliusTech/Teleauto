# RM500 当前设备双端口检查

设备：455LJ1710100UJ；ag501；Android 7.1.2 / V01.00.00.38。不同于此前安装系统探针的 455LJ3T2010192。当前设备的 com.dpad.service、com.dji 均已安装且运行。

用户确认操作左右摇杆、波轮、C1/C2、正面暂停键、返航及三段开关。双端口同时被动监听 90 秒，记录在 tcp-40007-active.jsonl / tcp-40009-active.jsonl；JSONL 内为接收相对时间及原始字节，不发送握手/控制报文。用户操作完成已确认，但本轮未逐键标记，不能据此建立具体位域映射。

40007 未收到数据；40009 仅见 00/81、00/82，负载各自不变。本机这轮输入活动没有观察到端口被唤醒，不能套用 rc701 的活动唤醒结论。

当前固件 link.json 已保存：40007 为 linkid=3/default_forwardid=2，40009 为 linkid=7/default_forwardid=2，后者 targets=[0x02,0x04]。这些是路由配置，不能仅据此解释完整握手或转发行为。二进制只读字符串分析未建立可验证的握手流程，未尝试任何猜测请求。

输入节点枚举：event0=cyttsp5_mt；event1=DJI RC (VID 2ca3/PID 1501)；event2=rk29-keypad；event3=lightsensor-level。该枚举只确定设备类型，不证明目标按钮属于 event1。系统 /dev/input 节点为 root:input 0660，普通 shell 不可直读；之前的特权采集被自动审批拒绝，未绕过执行。

固件 PushQueue 按命令集/命令号分发，未使用规则 sender/receiver 过滤。ComKeyManager 存在系统按键/摇杆拦截，但当前 user_setup_complete 和 device_provisioned 均为1，未发现首次设置未完成的问题。普通前台 KeyEvent 诊断没有收到目标按钮。

结论：仍未找到目标按钮的真实事件；没有修改生产 SDK 位域以冒充已支持。下一步需要可授权的全节点原始输入采集或更深入的固件路由分析；40007 与 event1 是独立通道。
