import 'teleauto_console.dart' show TeleautoConsoleGeometry;
import 'package:flutter/material.dart';
import 'teleauto_controls.dart';
import 'teleauto_models.dart';
import 'teleauto_tokens.dart';

/// Presentation-only control center. Application callbacks own persistence and hardware work.
class TeleautoControlCenter extends StatefulWidget {
  const TeleautoControlCenter({
    super.key,
    required this.devices,
    required this.onAction,
    required this.onBack,
    this.enabled = true,
    this.videoChannel = 161,
    this.videoBandwidth = 20,
    this.recording = false,
    this.deviceName = 'Teleauto',
    this.storage = const {},
    this.modules = const [],
    this.receivers = const [],
    this.inputSources = const [],
    this.version = '—',
    this.license = '—',
    this.initialPage = '设备管理',
  });
  final bool enabled, recording;
  final int videoChannel, videoBandwidth;
  final List<TeleautoDeviceEntry> devices;
  final List<Map<String, dynamic>> modules, receivers, inputSources;
  final Map<String, dynamic> storage;
  final String deviceName, version, license, initialPage;
  final void Function(String, Map<String, dynamic>) onAction;
  final VoidCallback onBack;
  @override
  State<TeleautoControlCenter> createState() => _TeleautoControlCenterState();
}

class _TeleautoControlCenterState extends State<TeleautoControlCenter> {
  late String page = widget.initialPage;
  String category = '航空设备', selected = 'GENERIC_AIRCRAFT';
  bool modulesOpen = true,
      settingsOpen = true,
      video = false,
      storageMenuOpen = false;
  late int channel = widget.videoChannel, bandwidth = widget.videoBandwidth;
  @override
  void didUpdateWidget(covariant TeleautoControlCenter oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (storageMenuOpen &&
        oldWidget.storage.toString() != widget.storage.toString()) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && storageMenuOpen) {
          Navigator.of(context).pop();
          storageMenuOpen = false;
        }
      });
    }
  }

  Widget button(String label, VoidCallback tap, {bool active = false}) =>
      SizedBox(
        height: TeleautoTokens.navigationHeight,
        child: TeleautoButton(
          label: label,
          usePanelSurface: true,
          onPressed: tap,
          active: active,
          fontSize: TeleautoTokens.bodyFont,
        ),
      );
  Widget title(String label) => Padding(
    padding: const EdgeInsets.only(bottom: TeleautoTokens.inset),
    child: Text(
      label,
      style: TeleautoTokens.textStyle(
        size: TeleautoTokens.titleFont,
        bold: true,
      ),
    ),
  );
  Widget card(Widget child, {bool selected = false}) => DecoratedBox(
    decoration: BoxDecoration(
      color: TeleautoTokens.surface,
      borderRadius: BorderRadius.circular(TeleautoTokens.cardRadius),
      border: Border.all(
        color: selected ? TeleautoTokens.secondary : TeleautoTokens.border,
        width: TeleautoTokens.hairline,
      ),
    ),
    child: Padding(
      padding: const EdgeInsets.all(TeleautoTokens.inset),
      child: child,
    ),
  );
  Widget navigationIcon(String label) {
    final icon = switch (label) {
      '设备管理' => Icons.devices_other_outlined,
      '新增设备' => Icons.add_circle_outline,
      '模块管理' => Icons.memory,
      '发射模块' => Icons.cell_tower,
      '图传模块' => Icons.image_outlined,
      '系统设置' => Icons.settings_outlined,
      '存储管理' => Icons.storage,
      '输入来源' => Icons.input,
      _ => Icons.info_outline,
    };
    return Icon(
      icon,
      size: TeleautoTokens.inset,
      color: TeleautoTokens.secondary,
    );
  }

  void action(String name, [Map<String, dynamic> values = const {}]) {
    if (widget.enabled) widget.onAction(name, values);
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, box) {
      final scale = (box.maxWidth / TeleautoTokens.baseWidth).clamp(.5, 3.0);
      final width = box.maxWidth / scale, height = box.maxHeight / scale;
      final headerScale =
          TeleautoConsoleGeometry(Size(box.maxWidth, box.maxHeight)).scale /
          scale;
      final navigation = [
        ('设备管理', false),
        ('新增设备', true),
        ('模块管理', false),
        if (modulesOpen) ...[('发射模块', true), ('图传模块', true)],
        ('系统设置', false),
        if (settingsOpen) ...[('存储管理', true), ('输入来源', true), ('版本信息', true)],
      ];
      return Material(
        type: MaterialType.transparency,
        child: ClipRect(
          child: OverflowBox(
            alignment: Alignment.topLeft,
            minWidth: width,
            maxWidth: width,
            minHeight: height,
            maxHeight: height,
            child: Transform.scale(
              scale: scale,
              alignment: Alignment.topLeft,
              child: SizedBox(
                width: width,
                height: height,
                child: Stack(
                  children: [
                    Positioned.fill(
                      child: Image.asset(
                        '${TeleautoTokens.assets}/png/background.png',
                        package: TeleautoTokens.package,
                        fit: BoxFit.cover,
                      ),
                    ),
                    Column(
                      children: [
                        SizedBox(
                          height: TeleautoTokens.topHeight * headerScale,
                          width: width,
                          child: FittedBox(
                            fit: BoxFit.fill,
                            child: SizedBox(
                              width: width / headerScale,
                              child: TeleautoTopBar(
                                child: Row(
                                  children: [
                                    const TeleautoIcon(
                                      'alius-logo',
                                      size: TeleautoTokens.icon,
                                    ),
                                    const SizedBox(width: TeleautoTokens.gap),
                                    Text(
                                      '控制中心',
                                      style: TeleautoTokens.textStyle(
                                        bold: true,
                                      ),
                                    ),
                                    Expanded(
                                      child: Text(
                                        widget.deviceName,
                                        textAlign: TextAlign.center,
                                        style: TeleautoTokens.textStyle(
                                          size: TeleautoTokens.bodyFont,
                                          bold: true,
                                        ),
                                      ),
                                    ),
                                    SizedBox(
                                      width: TeleautoTokens.footerHeight,
                                      height: TeleautoTokens.footerHeight,
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ),
                        Expanded(
                          child: Row(
                            children: [
                              SizedBox(
                                width: TeleautoTokens.centerSidebarWidth,
                                child: Column(
                                  children: [
                                    Expanded(
                                      child: ListView(
                                        padding: const EdgeInsets.all(
                                          TeleautoTokens.gap,
                                        ),
                                        children: [
                                          for (final item in navigation)
                                            Padding(
                                              padding: EdgeInsets.only(
                                                left: item.$2
                                                    ? TeleautoTokens.gap
                                                    : 0,
                                                bottom: TeleautoTokens.small,
                                              ),
                                              child: TeleautoNavigationItem(
                                                label: item.$1,
                                                icon: navigationIcon(item.$1),
                                                active: page == item.$1,
                                                expanded: item.$1 == '模块管理'
                                                    ? modulesOpen
                                                    : item.$1 == '系统设置'
                                                    ? settingsOpen
                                                    : null,
                                                onPressed: () => setState(() {
                                                  if (item.$1 == '模块管理') {
                                                    modulesOpen = !modulesOpen;
                                                  } else if (item.$1 ==
                                                      '系统设置') {
                                                    settingsOpen =
                                                        !settingsOpen;
                                                  } else {
                                                    page = item.$1;
                                                  }
                                                }),
                                              ),
                                            ),
                                        ],
                                      ),
                                    ),
                                    Padding(
                                      padding: const EdgeInsets.all(
                                        TeleautoTokens.gap,
                                      ),
                                      child: TeleautoNavigationItem(
                                        label: '返回控制台',
                                        icon: const Icon(
                                          Icons.chevron_left,
                                          size: TeleautoTokens.inset,
                                        ),
                                        onPressed: widget.onBack,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const VerticalDivider(
                                width: 1,
                                color: TeleautoTokens.border,
                              ),
                              Expanded(
                                child: Padding(
                                  padding: const EdgeInsets.all(
                                    TeleautoTokens.inset,
                                  ),
                                  child: AbsorbPointer(
                                    absorbing: !widget.enabled,
                                    child: Opacity(
                                      opacity: widget.enabled ? 1 : .55,
                                      child: _content(),
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    },
  );
  Widget _content() => switch (page) {
    '新增设备' => _create(),
    '设备管理' => _devices(),
    '存储管理' => _storage(),
    '版本信息' => ListView(
      children: [
        title('版本信息'),
        card(
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Teleauto ${widget.version}',
                style: TeleautoTokens.textStyle(),
              ),
              const SizedBox(height: TeleautoTokens.gap),
              if (widget.license.isNotEmpty)
                Text(
                  '授权状态：${widget.license}',
                  style: TeleautoTokens.textStyle(),
                ),
              const SizedBox(height: TeleautoTokens.inset),
              Text(
                'Flutter · Teleauto',
                style: TeleautoTokens.textStyle(
                  color: TeleautoTokens.secondary,
                ),
              ),
            ],
          ),
        ),
      ],
    ),
    '输入来源' => ListView(
      children: [
        title('输入来源'),
        card(
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('内置摇杆', style: TeleautoTokens.textStyle(bold: true)),
              const SizedBox(height: TeleautoTokens.gap),
              Text(
                widget.deviceName,
                style: TeleautoTokens.textStyle(
                  color: TeleautoTokens.secondary,
                ),
              ),
            ],
          ),
        ),
      ],
    ),
    _ => _modules(),
  };
  Widget _create() => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      title('新增设备'),
      Text(
        '选择设备类型并配置硬件能力',
        style: TeleautoTokens.textStyle(color: TeleautoTokens.secondary),
      ),
      const SizedBox(height: TeleautoTokens.inset),
      Row(
        children: [
          for (final name in ['航空设备', '地面设备', '水域设备', '机器人', '虚拟设备'])
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(right: TeleautoTokens.small),
                child: button(
                  name,
                  () => setState(() {
                    category = name;
                    selected = TeleautoDeviceType.all
                        .firstWhere((t) => t.category == name)
                        .id;
                    video = false;
                  }),
                  active: category == name,
                ),
              ),
            ),
        ],
      ),
      const SizedBox(height: TeleautoTokens.inset),
      Expanded(
        child: LayoutBuilder(
          builder: (context, constraints) => SingleChildScrollView(
            child: Wrap(
              spacing: TeleautoTokens.gap,
              runSpacing: TeleautoTokens.gap,
              children: [
                for (final type in TeleautoDeviceType.all.where(
                  (t) => t.category == category,
                ))
                  SizedBox(
                    width:
                        (constraints.maxWidth -
                            (TeleautoTokens.deviceGridColumns - 1) *
                                TeleautoTokens.gap) /
                        TeleautoTokens.deviceGridColumns,
                    child: card(
                      Column(
                        children: [
                          TeleautoIcon(
                            type.icon,
                            size: TeleautoTokens.iconLarge,
                          ),
                          const SizedBox(height: TeleautoTokens.gap),
                          SizedBox(
                            height: TeleautoTokens.deviceCardNameHeight,
                            child: Center(
                              child: Text(
                                type.name,
                                textAlign: TextAlign.center,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: TeleautoTokens.textStyle(bold: true),
                              ),
                            ),
                          ),
                          Text(
                            type.category,
                            style: TeleautoTokens.textStyle(
                              color: TeleautoTokens.secondary,
                            ),
                          ),
                          const SizedBox(height: TeleautoTokens.inset),
                          button(
                            selected == type.id ? '已选择' : '选择',
                            () => setState(() => selected = type.id),
                          ),
                        ],
                      ),
                      selected: selected == type.id,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
      if (category != '虚拟设备')
        CheckboxListTile(
          value: video,
          onChanged: widget.enabled
              ? (v) => setState(() => video = v ?? false)
              : null,
          title: Text('启用图传', style: TeleautoTokens.textStyle()),
        ),
      const SizedBox(height: TeleautoTokens.gap),
      Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          SizedBox(
            width: TeleautoTokens.sideWidth,
            child: button('返回设备管理', () => setState(() => page = '设备管理')),
          ),
          const SizedBox(width: TeleautoTokens.gap),
          SizedBox(
            width: TeleautoTokens.sideWidth,
            child: button('完成创建', () {
              if (widget.enabled) {
                action('createDevice', {'type': selected, 'video': video});
                setState(() => page = '设备管理');
              }
            }),
          ),
        ],
      ),
    ],
  );
  Widget _devices() => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      title('设备管理'),
      Expanded(
        child: widget.devices.isEmpty
            ? Center(child: Text('尚未创建设备', style: TeleautoTokens.textStyle()))
            : ListView(
                children: [
                  for (final device in widget.devices)
                    Padding(
                      padding: const EdgeInsets.only(
                        bottom: TeleautoTokens.gap,
                      ),
                      child: card(
                        Row(
                          children: [
                            TeleautoIcon(
                              _icon(device.type),
                              size: TeleautoTokens.iconLarge,
                            ),
                            const SizedBox(width: TeleautoTokens.inset),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    device.name,
                                    style: TeleautoTokens.textStyle(bold: true),
                                  ),
                                  Text(
                                    device.connected ? '已连接' : '未连接',
                                    style: TeleautoTokens.textStyle(
                                      color: TeleautoTokens.secondary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            SizedBox(
                              width: TeleautoTokens.keyWidth,
                              child: button(
                                device.active ? '使用中' : '启用',
                                () =>
                                    action('activateDevice', {'id': device.id}),
                                active: device.active,
                              ),
                            ),
                            const SizedBox(width: TeleautoTokens.gap),
                            SizedBox(
                              width: TeleautoTokens.keyWidth,
                              child: button(
                                '移除',
                                () => action('removeDevice', {'id': device.id}),
                              ),
                            ),
                          ],
                        ),
                        selected: device.active,
                      ),
                    ),
                ],
              ),
      ),
      button('新增设备', () => setState(() => page = '新增设备')),
    ],
  );
  String _icon(String type) =>
      TeleautoDeviceType.all
          .where((t) => t.id == type)
          .map((t) => t.icon)
          .firstOrNull ??
      'auth';
  Widget _storage() {
    final current = widget.storage['selected'] == 'sd' ? '存储卡' : '内部存储';
    final hasCard = widget.storage['cardPresent'] == true;
    final writable = widget.storage['cardWritable'] == true;
    final alternate = current == '存储卡' ? '内部存储' : '存储卡';
    return ListView(
      children: [
        title('存储管理'),
        card(
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('保存位置', style: TeleautoTokens.textStyle(bold: true)),
              const SizedBox(height: TeleautoTokens.inset),
              if (hasCard)
                PopupMenuButton<String>(
                  enabled: widget.enabled,
                  onOpened: () => storageMenuOpen = true,
                  onCanceled: () => storageMenuOpen = false,
                  tooltip: '切换保存位置',
                  position: PopupMenuPosition.under,
                  color: TeleautoTokens.surface,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(TeleautoTokens.radius),
                  ),
                  onSelected: (value) {
                    storageMenuOpen = false;
                    action('selectStorage', {'location': value});
                  },
                  itemBuilder: (_) => [
                    PopupMenuItem(
                      value: current == '存储卡' ? 'internal' : 'sd',
                      enabled: current == '存储卡' || writable,
                      child: Text(
                        '$alternate${current != '存储卡' && !writable ? '（不可用）' : ''}',
                        style: TeleautoTokens.textStyle(),
                      ),
                    ),
                  ],
                  child: SizedBox(
                    height: TeleautoTokens.footerHeight,
                    child: TeleautoMaterial(
                      'key_raised',
                      child: Center(
                        child: Text(
                          '$current  ▾',
                          style: TeleautoTokens.textStyle(bold: true),
                        ),
                      ),
                    ),
                  ),
                )
              else
                button('内部存储', () => action('refreshStorage')),
              const SizedBox(height: TeleautoTokens.inset),
              Text(
                '${widget.storage['capacity'] ?? '正在读取容量…'}',
                style: TeleautoTokens.textStyle(),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _modules() => ListView(
    children: [
      title(page),
      if (page == '图传模块') ...[
        Text('频道与带宽', style: TeleautoTokens.textStyle(bold: true)),
        DropdownButton<int>(
          value: channel,
          isExpanded: true,
          items: [
            for (final v in List<int>.generate(196, (i) => i + 1))
              DropdownMenuItem(value: v, child: Text('频道 $v')),
          ],
          onChanged: widget.enabled
              ? (v) => setState(() => channel = v!)
              : null,
        ),
        DropdownButton<int>(
          value: bandwidth,
          isExpanded: true,
          items: [
            for (final v in [20, 40])
              DropdownMenuItem(value: v, child: Text('$v MHz')),
          ],
          onChanged: widget.enabled
              ? (v) => setState(() => bandwidth = v!)
              : null,
        ),
        button(
          '应用接收设置',
          () => action('videoConfig', {
            'channel': channel,
            'bandwidth': bandwidth,
          }),
        ),
        const SizedBox(height: TeleautoTokens.gap),
        button('拍照', () => action('capture')),
        const SizedBox(height: TeleautoTokens.gap),
        button(widget.recording ? '停止录像' : '开始录像', () => action('record')),
        const SizedBox(height: TeleautoTokens.gap),
        button('开启图传', () => action('videoEnabled', {'enabled': true})),
        const SizedBox(height: TeleautoTokens.gap),
        button('停止图传', () => action('videoEnabled', {'enabled': false})),
      ],
      const SizedBox(height: TeleautoTokens.inset),
      if (widget.modules.isEmpty)
        Text(
          '未发现已连接模块',
          style: TeleautoTokens.textStyle(color: TeleautoTokens.secondary),
        ),
      for (final module in widget.modules.where(
        (m) => m['kind'] == (page == '图传模块' ? 'video' : 'transmitter'),
      ))
        Padding(
          padding: const EdgeInsets.only(bottom: TeleautoTokens.gap),
          child: card(
            Text(
              '${module['name']}\n${module['status']}',
              style: TeleautoTokens.textStyle(),
            ),
          ),
        ),
    ],
  );
}
