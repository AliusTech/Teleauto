COMMAND: /opt/homebrew/opt/llvm/bin/llvm-objdump -d --start-address=94720 --stop-address=94928 /tmp/rm700_recon/dji_config_store

/tmp/rm700_recon/dji_config_store:	file format elf64-littleaarch64

Disassembly of section .text:

0000000000015a84 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m>:
   17200: 912bd800     	add	x0, x0, #0xaf6
   17204: 9117a021     	add	x1, x1, #0x5e8
   17208: 52805c62     	mov	w2, #0x2e3              // =739
   1720c: 9400891d     	bl	0x39680 <printf@plt>
   17210: 9400848d     	bl	0x38444 <_ZNSt3__113basic_filebufIcNS_11char_traitsIcEEEC2Ev+0x3440>
   17214: 34000ec0     	cbz	w0, 0x173ec <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1968>
   17218: 90ffff80     	adrp	x0, 0x7000
   1721c: f0ffff81     	adrp	x1, 0xa000
   17220: 9121a000     	add	x0, x0, #0x868
   17224: 9117a021     	add	x1, x1, #0x5e8
   17228: 52805c62     	mov	w2, #0x2e3              // =739
   1722c: 94008488     	bl	0x3844c <_ZNSt3__113basic_filebufIcNS_11char_traitsIcEEEC2Ev+0x3448>
   17230: 17ffff80     	b	0x17030 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x15ac>
   17234: 6f00e400     	movi	v0.2d, #0000000000000000
   17238: 3c8b82a0     	stur	q0, [x21, #0xb8]
   1723c: 3c8a82a0     	stur	q0, [x21, #0xa8]
   17240: 3c8982a0     	stur	q0, [x21, #0x98]
   17244: 3c8882a0     	stur	q0, [x21, #0x88]
   17248: 3c8782a0     	stur	q0, [x21, #0x78]
   1724c: 3c8682a0     	stur	q0, [x21, #0x68]
   17250: 3c8582a0     	stur	q0, [x21, #0x58]
   17254: 3c8482a0     	stur	q0, [x21, #0x48]
   17258: d0ffff84     	adrp	x4, 0x9000
   1725c: f0ffff85     	adrp	x5, 0xa000
   17260: 910b6084     	add	x4, x4, #0x2d8
   17264: 9117a0a5     	add	x5, x5, #0x5e8
   17268: 910343e2     	add	x2, sp, #0xd0
   1726c: 52800b40     	mov	w0, #0x5a               // =90
   17270: 52800021     	mov	w1, #0x1                // =1
   17274: 52801003     	mov	w3, #0x80               // =128
   17278: 52805d26     	mov	w6, #0x2e9              // =745
   1727c: 94008905     	bl	0x39690 <duss_log_gen_head@plt>
   17280: 34000160     	cbz	w0, 0x172ac <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1828>
   17284: 90ffffa3     	adrp	x3, 0xb000
   17288: f0ffff84     	adrp	x4, 0xa000
   1728c: 9111b463     	add	x3, x3, #0x46d
   17290: 9117a084     	add	x4, x4, #0x5e8
   17294: 910343e2     	add	x2, sp, #0xd0
   17298: 910543e6     	add	x6, sp, #0x150
   1729c: 52800b40     	mov	w0, #0x5a               // =90
   172a0: 52800021     	mov	w1, #0x1                // =1
   172a4: 52805d25     	mov	w5, #0x2e9              // =745
   172a8: 940088fe     	bl	0x396a0 <duss_log_print@plt>
   172ac: 910543e0     	add	x0, sp, #0x150
   172b0: 940089b8     	bl	0x39990 <system@plt>
   172b4: 394083e8     	ldrb	w8, [sp, #0x20]
   172b8: f9401be9     	ldr	x9, [sp, #0x30]
   172bc: d10243a1     	sub	x1, x29, #0x90
   172c0: 7200011f     	tst	w8, #0x1
   172c4: 9a890280     	csel	x0, x20, x9, eq
   172c8: 940089ae     	bl	0x39980 <stat@plt>
   172cc: 7100001f     	cmp	w0, #0x0

COMMAND: /opt/homebrew/opt/llvm/bin/llvm-objdump -d --start-address=95716 --stop-address=96192 /tmp/rm700_recon/dji_config_store

/tmp/rm700_recon/dji_config_store:	file format elf64-littleaarch64

Disassembly of section .text:

0000000000015a84 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m>:
   175e4: d102c3ff     	sub	sp, sp, #0xb0
   175e8: a9057bfd     	stp	x29, x30, [sp, #0x50]
   175ec: f90033fb     	str	x27, [sp, #0x60]
   175f0: a90767fa     	stp	x26, x25, [sp, #0x70]
   175f4: a9085ff8     	stp	x24, x23, [sp, #0x80]
   175f8: a90957f6     	stp	x22, x21, [sp, #0x90]
   175fc: a90a4ff4     	stp	x20, x19, [sp, #0xa0]
   17600: 910143fd     	add	x29, sp, #0x50
   17604: d53bd055     	mrs	x21, TPIDR_EL0
   17608: f94016a9     	ldr	x9, [x21, #0x28]
   1760c: 910003e8     	mov	x8, sp
   17610: f0000117     	adrp	x23, 0x3a000
   17614: d0000138     	adrp	x24, 0x3d000
   17618: d0000133     	adrp	x19, 0x3d000
   1761c: aa1f03f6     	mov	x22, xzr
   17620: 910162f7     	add	x23, x23, #0x58
   17624: 91270318     	add	x24, x24, #0x9c0
   17628: 9126e273     	add	x19, x19, #0x9b8
   1762c: 91002119     	add	x25, x8, #0x8
   17630: 9100611a     	add	x26, x8, #0x18
   17634: d000013b     	adrp	x27, 0x3d000
   17638: f81f83a9     	stur	x9, [x29, #-0x8]
   1763c: 14000006     	b	0x17654 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1bd0>
   17640: 910003e0     	mov	x0, sp
   17644: 94008853     	bl	0x39790 <_ZNSt3__16localeD1Ev@plt>
   17648: 910006d6     	add	x22, x22, #0x1
   1764c: f10016df     	cmp	x22, #0x5
   17650: 54000680     	b.eq	0x17720 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1c9c>
   17654: f8767ae1     	ldr	x1, [x23, x22, lsl #3]
   17658: 6f00e400     	movi	v0.2d, #0000000000000000
   1765c: 910003e0     	mov	x0, sp
   17660: 2a1f03e2     	mov	w2, wzr
   17664: ad0103e0     	stp	q0, q0, [sp, #0x20]
   17668: ad0003e0     	stp	q0, q0, [sp]
   1766c: 94000057     	bl	0x177c8 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1d44>
   17670: a9402314     	ldp	x20, x8, [x24]
   17674: eb08029f     	cmp	x20, x8
   17678: 54000300     	b.eq	0x176d8 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1c54>
   1767c: 910003e1     	mov	x1, sp
   17680: aa1403e0     	mov	x0, x20
   17684: 940088cb     	bl	0x399b0 <_ZNSt3__16localeC1ERKS0_@plt>
   17688: 3dc00320     	ldr	q0, [x25]
   1768c: 3c808280     	stur	q0, [x20, #0x8]
   17690: 3dc00340     	ldr	q0, [x26]
   17694: 3c818280     	stur	q0, [x20, #0x18]
   17698: f94017e8     	ldr	x8, [sp, #0x28]
   1769c: f9001688     	str	x8, [x20, #0x28]
   176a0: f9401be8     	ldr	x8, [sp, #0x30]
   176a4: f9001a88     	str	x8, [x20, #0x30]
   176a8: b40000c8     	cbz	x8, 0x176c0 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1c3c>
   176ac: 91002108     	add	x8, x8, #0x8
   176b0: c85f7d09     	ldxr	x9, [x8]
   176b4: 91000529     	add	x9, x9, #0x1
   176b8: c80a7d09     	stxr	w10, x9, [x8]
   176bc: 35ffffaa     	cbnz	w10, 0x176b0 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1c2c>
   176c0: f9401fe8     	ldr	x8, [sp, #0x38]
   176c4: f9001e88     	str	x8, [x20, #0x38]
   176c8: f944e368     	ldr	x8, [x27, #0x9c0]
   176cc: 91010108     	add	x8, x8, #0x40
   176d0: f904e368     	str	x8, [x27, #0x9c0]
   176d4: 14000004     	b	0x176e4 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1c60>
   176d8: 910003e1     	mov	x1, sp
   176dc: aa1303e0     	mov	x0, x19
   176e0: 9400402c     	bl	0x27790 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x11d0c>
   176e4: f9401bf4     	ldr	x20, [sp, #0x30]
   176e8: b4fffad4     	cbz	x20, 0x17640 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1bbc>
   176ec: 91002288     	add	x8, x20, #0x8
   176f0: c85ffd09     	ldaxr	x9, [x8]
   176f4: d100052a     	sub	x10, x9, #0x1
   176f8: c80bfd0a     	stlxr	w11, x10, [x8]
   176fc: 35ffffab     	cbnz	w11, 0x176f0 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1c6c>
   17700: b5fffa09     	cbnz	x9, 0x17640 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1bbc>
   17704: f9400288     	ldr	x8, [x20]
   17708: aa1403e0     	mov	x0, x20
   1770c: f9400908     	ldr	x8, [x8, #0x10]
   17710: d63f0100     	blr	x8
   17714: aa1403e0     	mov	x0, x20
   17718: 94008876     	bl	0x398f0 <_ZNSt3__119__shared_weak_count14__release_weakEv@plt>
   1771c: 17ffffc9     	b	0x17640 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1bbc>
   17720: 90ffff80     	adrp	x0, 0x7000
   17724: 91239000     	add	x0, x0, #0x8e4
   17728: 94000074     	bl	0x178f8 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1e74>
   1772c: d0ffff60     	adrp	x0, 0x5000
   17730: 912c3400     	add	x0, x0, #0xb0d
   17734: 94000071     	bl	0x178f8 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1e74>
   17738: d0ffff60     	adrp	x0, 0x5000
   1773c: 9119a400     	add	x0, x0, #0x669
   17740: 9400006e     	bl	0x178f8 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1e74>
   17744: 90ffff80     	adrp	x0, 0x7000
   17748: 91105800     	add	x0, x0, #0x416
   1774c: 9400006b     	bl	0x178f8 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1e74>
   17750: d0000120     	adrp	x0, 0x3d000
   17754: 91308000     	add	x0, x0, #0xc20
   17758: aa1f03e1     	mov	x1, xzr
   1775c: 94008899     	bl	0x399c0 <pthread_mutex_init@plt>
   17760: d0000133     	adrp	x19, 0x3d000
   17764: 912fa273     	add	x19, x19, #0xbe8
   17768: 52800034     	mov	w20, #0x1               // =1
   1776c: 91004260     	add	x0, x19, #0x10
   17770: aa1f03e1     	mov	x1, xzr
   17774: 39000674     	strb	w20, [x19, #0x1]
   17778: 94008892     	bl	0x399c0 <pthread_mutex_init@plt>
   1777c: 79000274     	strh	w20, [x19]
   17780: f94016a8     	ldr	x8, [x21, #0x28]
   17784: f85f83a9     	ldur	x9, [x29, #-0x8]
   17788: eb09011f     	cmp	x8, x9
   1778c: 54000121     	b.ne	0x177b0 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1d2c>
   17790: a94a4ff4     	ldp	x20, x19, [sp, #0xa0]
   17794: a94957f6     	ldp	x22, x21, [sp, #0x90]
   17798: a9485ff8     	ldp	x24, x23, [sp, #0x80]
   1779c: a94767fa     	ldp	x26, x25, [sp, #0x70]
   177a0: f94033fb     	ldr	x27, [sp, #0x60]
   177a4: a9457bfd     	ldp	x29, x30, [sp, #0x50]
   177a8: 9102c3ff     	add	sp, sp, #0xb0
   177ac: d65f03c0     	ret
   177b0: 940087c0     	bl	0x396b0 <__stack_chk_fail@plt>
   177b4: aa0003f3     	mov	x19, x0
   177b8: 910003e0     	mov	x0, sp
   177bc: 94000039     	bl	0x178a0 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x1e1c>

COMMAND: /opt/homebrew/opt/llvm/bin/llvm-objdump -d --start-address=100480 --stop-address=101072 /tmp/rm700_recon/dji_config_store

/tmp/rm700_recon/dji_config_store:	file format elf64-littleaarch64

Disassembly of section .text:

0000000000015a84 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m>:
   18880: 52800061     	mov	w1, #0x3                // =3
   18884: 5280c525     	mov	w5, #0x629              // =1577
   18888: 1400001a     	b	0x188f0 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2e6c>
   1888c: b0ffff84     	adrp	x4, 0x9000
   18890: d0ffff65     	adrp	x5, 0x6000
   18894: 6f00e400     	movi	v0.2d, #0000000000000000
   18898: 910b6084     	add	x4, x4, #0x2d8
   1889c: 91334ca5     	add	x5, x5, #0xcd3
   188a0: 910103e2     	add	x2, sp, #0x40
   188a4: 52800b40     	mov	w0, #0x5a               // =90
   188a8: 52800061     	mov	w1, #0x3                // =3
   188ac: 52801003     	mov	w3, #0x80               // =128
   188b0: 5280c706     	mov	w6, #0x638              // =1592
   188b4: ad0302a0     	stp	q0, q0, [x21, #0x60]
   188b8: 3d8016a0     	str	q0, [x21, #0x50]
   188bc: ad0383e0     	stp	q0, q0, [sp, #0x70]
   188c0: ad0283e0     	stp	q0, q0, [sp, #0x50]
   188c4: 3d8013e0     	str	q0, [sp, #0x40]
   188c8: 94008372     	bl	0x39690 <duss_log_gen_head@plt>
   188cc: 34000140     	cbz	w0, 0x188f4 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2e70>
   188d0: f0ffff63     	adrp	x3, 0x7000
   188d4: d0ffff64     	adrp	x4, 0x6000
   188d8: 9120e063     	add	x3, x3, #0x838
   188dc: 91334c84     	add	x4, x4, #0xcd3
   188e0: 910103e2     	add	x2, sp, #0x40
   188e4: 52800b40     	mov	w0, #0x5a               // =90
   188e8: 52800061     	mov	w1, #0x3                // =3
   188ec: 5280c705     	mov	w5, #0x638              // =1592
   188f0: 9400836c     	bl	0x396a0 <duss_log_print@plt>
   188f4: f9401688     	ldr	x8, [x20, #0x28]
   188f8: f85f83a9     	ldur	x9, [x29, #-0x8]
   188fc: eb09011f     	cmp	x8, x9
   18900: 54000101     	b.ne	0x18920 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2e9c>
   18904: 2a1303e0     	mov	w0, w19
   18908: a9514ff4     	ldp	x20, x19, [sp, #0x110]
   1890c: a95057f6     	ldp	x22, x21, [sp, #0x100]
   18910: f9407bfc     	ldr	x28, [sp, #0xf0]
   18914: a94e7bfd     	ldp	x29, x30, [sp, #0xe0]
   18918: 910483ff     	add	sp, sp, #0x120
   1891c: d65f03c0     	ret
   18920: 94008364     	bl	0x396b0 <__stack_chk_fail@plt>
   18924: d10343ff     	sub	sp, sp, #0xd0
   18928: a9097bfd     	stp	x29, x30, [sp, #0x90]
   1892c: f90053f7     	str	x23, [sp, #0xa0]
   18930: a90b57f6     	stp	x22, x21, [sp, #0xb0]
   18934: a90c4ff4     	stp	x20, x19, [sp, #0xc0]
   18938: 910243fd     	add	x29, sp, #0x90
   1893c: d53bd055     	mrs	x21, TPIDR_EL0
   18940: f94016a8     	ldr	x8, [x21, #0x28]
   18944: b000012a     	adrp	x10, 0x3d000
   18948: 9126e14a     	add	x10, x10, #0x9b8
   1894c: aa0003f3     	mov	x19, x0
   18950: f81f83a8     	stur	x8, [x29, #-0x8]
   18954: 39400009     	ldrb	w9, [x0]
   18958: a9402954     	ldp	x20, x10, [x10]
   1895c: 7200013f     	tst	w9, #0x1
   18960: 1a9f17e8     	cset	w8, eq
   18964: eb0a029f     	cmp	x20, x10
   18968: 54000280     	b.eq	0x189b8 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2f34>
   1896c: 91000676     	add	x22, x19, #0x1
   18970: b0000137     	adrp	x23, 0x3d000
   18974: a940aa6b     	ldp	x11, x10, [x19, #0x8]
   18978: 53011d29     	ubfx	w9, w9, #1, #7
   1897c: 7200011f     	tst	w8, #0x1
   18980: aa1403e2     	mov	x2, x20
   18984: 9a8a12c0     	csel	x0, x22, x10, ne
   18988: 9a8b1128     	csel	x8, x9, x11, ne
   1898c: 8b080001     	add	x1, x0, x8
   18990: 2a1f03e3     	mov	w3, wzr
   18994: 94000074     	bl	0x18b64 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x30e0>
   18998: 37000300     	tbnz	w0, #0x0, 0x189f8 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2f74>
   1899c: 39400269     	ldrb	w9, [x19]
   189a0: f944e2ea     	ldr	x10, [x23, #0x9c0]
   189a4: 91010294     	add	x20, x20, #0x40
   189a8: 7200013f     	tst	w9, #0x1
   189ac: 1a9f17e8     	cset	w8, eq
   189b0: eb0a029f     	cmp	x20, x10
   189b4: 54fffe01     	b.ne	0x18974 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2ef0>
   189b8: 36000068     	tbz	w8, #0x0, 0x189c4 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2f40>
   189bc: 91000663     	add	x3, x19, #0x1
   189c0: 14000002     	b	0x189c8 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2f44>
   189c4: f9400a63     	ldr	x3, [x19, #0x10]
   189c8: f0ffff80     	adrp	x0, 0xb000
   189cc: d0ffff61     	adrp	x1, 0x6000
   189d0: 91030400     	add	x0, x0, #0xc1
   189d4: 91257421     	add	x1, x1, #0x95d
   189d8: 528024e2     	mov	w2, #0x127              // =295
   189dc: 94008329     	bl	0x39680 <printf@plt>
   189e0: 94007e99     	bl	0x38444 <_ZNSt3__113basic_filebufIcNS_11char_traitsIcEEEC2Ev+0x3440>
   189e4: 340000e0     	cbz	w0, 0x18a00 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2f7c>
   189e8: 39400268     	ldrb	w8, [x19]
   189ec: 37000328     	tbnz	w8, #0x0, 0x18a50 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2fcc>
   189f0: 91000663     	add	x3, x19, #0x1
   189f4: 14000018     	b	0x18a54 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2fd0>
   189f8: 52800020     	mov	w0, #0x1                // =1
   189fc: 14000028     	b	0x18a9c <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x3018>
   18a00: b0ffff84     	adrp	x4, 0x9000
   18a04: d0ffff65     	adrp	x5, 0x6000
   18a08: 6f00e400     	movi	v0.2d, #0000000000000000
   18a0c: 910b6084     	add	x4, x4, #0x2d8
   18a10: 912574a5     	add	x5, x5, #0x95d
   18a14: 910003e2     	mov	x2, sp
   18a18: 52800b40     	mov	w0, #0x5a               // =90
   18a1c: 52800021     	mov	w1, #0x1                // =1
   18a20: 52801003     	mov	w3, #0x80               // =128
   18a24: 528024e6     	mov	w6, #0x127              // =295
   18a28: ad0303e0     	stp	q0, q0, [sp, #0x60]
   18a2c: ad0203e0     	stp	q0, q0, [sp, #0x40]
   18a30: ad0103e0     	stp	q0, q0, [sp, #0x20]
   18a34: ad0003e0     	stp	q0, q0, [sp]
   18a38: 94008316     	bl	0x39690 <duss_log_gen_head@plt>
   18a3c: 340002e0     	cbz	w0, 0x18a98 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x3014>
   18a40: 39400268     	ldrb	w8, [x19]
   18a44: 37000168     	tbnz	w8, #0x0, 0x18a70 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2fec>
   18a48: 91000666     	add	x6, x19, #0x1
   18a4c: 1400000a     	b	0x18a74 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x2ff0>
   18a50: f9400a63     	ldr	x3, [x19, #0x10]
   18a54: d0ffff80     	adrp	x0, 0xa000
   18a58: d0ffff61     	adrp	x1, 0x6000
   18a5c: 91320c00     	add	x0, x0, #0xc83
   18a60: 91257421     	add	x1, x1, #0x95d
   18a64: 528024e2     	mov	w2, #0x127              // =295
   18a68: 94007e79     	bl	0x3844c <_ZNSt3__113basic_filebufIcNS_11char_traitsIcEEEC2Ev+0x3448>
   18a6c: 1400000b     	b	0x18a98 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x3014>
   18a70: f9400a66     	ldr	x6, [x19, #0x10]
   18a74: 90ffffa3     	adrp	x3, 0xc000
   18a78: d0ffff64     	adrp	x4, 0x6000
   18a7c: 91069863     	add	x3, x3, #0x1a6
   18a80: 91257484     	add	x4, x4, #0x95d
   18a84: 910003e2     	mov	x2, sp
   18a88: 52800b40     	mov	w0, #0x5a               // =90
   18a8c: 52800021     	mov	w1, #0x1                // =1
   18a90: 528024e5     	mov	w5, #0x127              // =295
   18a94: 94008303     	bl	0x396a0 <duss_log_print@plt>
   18a98: 2a1f03e0     	mov	w0, wzr
   18a9c: f94016a8     	ldr	x8, [x21, #0x28]
   18aa0: f85f83a9     	ldur	x9, [x29, #-0x8]
   18aa4: eb09011f     	cmp	x8, x9
   18aa8: 540000e1     	b.ne	0x18ac4 <_ZNSt3__124__put_character_sequenceIcNS_11char_traitsIcEEEERNS_13basic_ostreamIT_T0_EES7_PKS4_m+0x3040>
   18aac: a94c4ff4     	ldp	x20, x19, [sp, #0xc0]
   18ab0: a94b57f6     	ldp	x22, x21, [sp, #0xb0]
   18ab4: f94053f7     	ldr	x23, [sp, #0xa0]
   18ab8: a9497bfd     	ldp	x29, x30, [sp, #0x90]
   18abc: 910343ff     	add	sp, sp, #0xd0
   18ac0: d65f03c0     	ret
   18ac4: 940082fb     	bl	0x396b0 <__stack_chk_fail@plt>
   18ac8: d10443ff     	sub	sp, sp, #0x110
   18acc: a90f7bfd     	stp	x29, x30, [sp, #0xf0]
