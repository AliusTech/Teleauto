# USB HID 配置权限路径审计

## 结论

本轮完成三个候选服务的限定静态审计及设备只读复核。**没有候选达到“可准备受控验证”的门槛**；dji_amt 是后续离线审计优先项，但不等于已取得ROOT或configfs权限。HID输出尚未实现。

对象为 RC701，序列号7XJCM3L00111DE，固件 DJI/rc701/rc701:11/V01.15.03.89/389:user/release-keys。不得推广为RM700实测结论。

## 候选清单

| 候选 | 分类 | 已确认的调用链与边界 | 与HID的关系 / 缺口 |
| --- | --- | --- | --- |
| 标准UsbManager、现有USB init模式 | 已排除：作为现成HID入口 | 前轮已确认功能枚举不含hid，MANAGE_USB也不允许任意configfs操作 | 不提供所需接口；不是宣称整个USB栈无缺陷 |
| usb_bulk_raw | 已排除：作为现成配置接口 | 前轮导入和字符串指向固定FunctionFS bulk描述符写入；无已发现HID管理接口 | root运行不等于调用者获得配置能力 |
| dji_amt任务入口 | 证据不足，优先离线跟进 | 外部来源未确认→DUSS事件注册表→sys_amt_task_start→工作线程→popen；init及实测UID均为0 | 具备潜在执行能力，但传输可达性、制造状态、授权和参数约束尚未闭合；不是专用HID接口 |
| dji_amt安全/OTP分支 | 已排除：本阶段验证范围 | 安全处理函数分派到生产/OTP回调，相关脚本执行共用线程机制 | 存在不可逆副作用；仅静态记录，不测试或借用 |
| dji_config_store | 证据不足 | init属性触发root服务→DUSS导入/导出处理→文件及参数操作；部分复制、删除、导入路径调用file_path_filter | 未证明能指定任意目标路径或写configfs；当前进程列表未见其运行 |
| upgrade_test | 证据不足；本轮不做运行验证 | root init服务→固定启动脚本→参数文件→升级命令分派；load_saved_cmd另有system调用 | 写入能力属于升级业务，不能等同任意配置能力；启动权限、参数文件控制权和安全调用条件未确认 |
| protocol send(Pack) | 证据不足：沿用前轮 | 前轮原始指令发现方法缺少同类权限检查，但完整Binder/传输可达性未验证 | 仅涉及发包，不能推导ROOT；本轮未调用 |

## 原始指令核对

通过ELF `.gnu_debugdata` 解压恢复符号，再用 llvm-objdump 检查原始AArch64指令；符号、调用点与关键范围已保存。地址为ELF虚拟地址，并非设备运行时地址。

### dji_amt

- 三个二进制本地与设备SHA256逐一相同，见binary-manifest.json及files.txt。
- `main`调用配置选择/初始化，再调用`sys_amt_all_event_start`；该函数创建路由表和DUSS事件client。重定位表含任务处理及安全处理函数指针。共享库内部路由与调用方身份校验未完整恢复，不能声称外部请求已经可达。
- `sys_amt_task_start`（0x41c8）检查请求字段、任务状态，并调用`dji_get_state_from_cmdline`。后者读取engineering/factory/production制造状态；特定生产状态分支调用`qsee_parameter_read`。因此不能简单归结为“没有鉴权”。
- 请求数据被拷入任务结构，处理函数内的线程启动指向`amt_alloc_and_run_task_thread`；该线程0x4b28调用popen。存在任务资源、计时及其他检查；没有完成全部参数有效范围或命令限制证明，不构造输入。
- `sys_amt_task_check_securedebug_auth`（0x7118）确实只有返回0的两条指令。但返回码语义及它是否控制任务入口尚未确认；不能据此认定鉴权绕过。
- `script_exec`的已识别直接调用方包括OTP/生产分支，不能因为名字通用而当成独立的安全执行API。
- 设备普通shell无法读取/proc/cmdline；没有绕过权限，也没有调用QSEE来探测。当前制造状态留为未知。

### dji_config_store

- `djiconfigstore.rc`：disabled、user/group root，通过dji.configstore属性启动/停止。二进制0755且无setuid位，shell直接启动不会自动成为root。
- 已恢复导入、导出、复制及删除处理函数。`file_path_filter`调用regex_match；部分调用点见calls文件。存在/data/export路径规则，但未证明所有操作和所有配置均受同样限制。
- 导出回调0x172b0存在system调用，这是业务执行点，不是任意命令接口证明。
- 本轮ls显示/data/export与/data/upgrade_test_param不存在；/data为system:system 0771，当前shell无目录创建权限。未发现可由shell写入的现成配置入口，未修改属性来启动服务。

### upgrade_test

- init定义disabled、oneshot、root，通过start_upgrade_test.sh启动。脚本默认读取/data/upgrade_test_param，可受服务环境中的DJI_UPGRADE_TEST_PARAM_FILE影响；未证明shell能设置其启动环境。
- 脚本使用 `upgrade_test $(cat "$TEST_PARAM_FILE")` 的等价未加引号展开形式：会产生分词/通配展开，**不意味着参数中的shell控制符被再次解释**。不能直接将它报成命令注入。
- `load_saved_cmd`在0xc44c调用strstr检查包含upgrade_test，0xc458才调用system。该检查不是完整安全证明；保存命令的数据来源、写权限及实际调用条件仍需补全。
- 未调用help/test/load_cmd或任何升级子命令，避免在尚未确定副作用时执行工具。

## 设备状态与审计边界

本轮前后：普通shell uid2000、SELinux Permissive、USB config/state均为mtp,bulk,adb、控制器a600000.dwc3；结束时UDC为configured且ADB命令成功。Permissive不取消Unix文件权限，g1/g2仍为root:root 0770。

设备只读命令逐条记录在commands.jsonl，对应输出含stderr。部分复合命令最终退出码为0，但其中的Permission denied仍作为失败证据保留；未把管道退出码当成全部查询成功。

没有执行目标二进制、发包、设置属性、启动服务、修改configfs、重启、解锁、刷写或提权。仅新增审计证据和文档，没有改SDK、应用或公共API；不需要构建或发布SDK。原有工作保留，未提交。

## 后续门槛

优先补全dji_amt所用DUSS共享库的路由选择、注册分派和来源校验，结合实际被选中的配置文件（示例dji_0.json不等于已证明的运行配置）。进一步确认制造状态及授权检查语义，但不以运行制造测试来取证。

只有完整证明候选入口的调用权限、参数边界与副作用，才能提出具体最小实验。当前不能交付可运行的HID切换或回滚脚本。任何后续实验仍需先建立真实gadget配置备份、独立恢复通道和设备侧超时回滚，再按用户约定确认。
