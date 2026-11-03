COMMAND: /opt/homebrew/opt/llvm/bin/llvm-objdump -d --start-address=49776 --stop-address=50284 /tmp/rm700_recon/upgrade_test

/tmp/rm700_recon/upgrade_test:	file format elf64-littleaarch64

Disassembly of section .text:

000000000000a000 <.text>:
    c270: 2a0003f3     	mov	w19, w0
    c274: aa1403e0     	mov	x0, x20
    c278: 94001d7a     	bl	0x13860 <free@plt>
    c27c: 2a1303e0     	mov	w0, w19
    c280: a9444ff4     	ldp	x20, x19, [sp, #0x40]
    c284: a94357f6     	ldp	x22, x21, [sp, #0x30]
    c288: a9425ff8     	ldp	x24, x23, [sp, #0x20]
    c28c: f9400bf9     	ldr	x25, [sp, #0x10]
    c290: a8c57bfd     	ldp	x29, x30, [sp], #0x50
    c294: d65f03c0     	ret
    c298: d10103ff     	sub	sp, sp, #0x40
    c29c: a9017bfd     	stp	x29, x30, [sp, #0x10]
    c2a0: f90013f5     	str	x21, [sp, #0x20]
    c2a4: a9034ff4     	stp	x20, x19, [sp, #0x30]
    c2a8: 910043fd     	add	x29, sp, #0x10
    c2ac: d53bd055     	mrs	x21, TPIDR_EL0
    c2b0: f94016a8     	ldr	x8, [x21, #0x28]
    c2b4: d0ffffc0     	adrp	x0, 0x6000
    c2b8: 9133b400     	add	x0, x0, #0xced
    c2bc: 910013e1     	add	x1, sp, #0x4
    c2c0: f90007e8     	str	x8, [sp, #0x8]
    c2c4: b90007ff     	str	wzr, [sp, #0x4]
    c2c8: 94001de2     	bl	0x13a50 <upgrade_info_get_int_tag@plt>
    c2cc: 34000140     	cbz	w0, 0xc2f4 <.text+0x22f4>
    c2d0: 90ffffe1     	adrp	x1, 0x8000
    c2d4: f0ffffa3     	adrp	x3, 0x3000
    c2d8: 9115f821     	add	x1, x1, #0x57e
    c2dc: 910b3c63     	add	x3, x3, #0x2cf
    c2e0: 528037a2     	mov	w2, #0x1bd              // =445
    c2e4: 2a1f03e0     	mov	w0, wzr
    c2e8: 94001dde     	bl	0x13a60 <up_logf@plt>
    c2ec: 2a1f03e0     	mov	w0, wzr
    c2f0: 1400003a     	b	0xc3d8 <.text+0x23d8>
    c2f4: b98007f4     	ldrsw	x20, [sp, #0x4]
    c2f8: 52800021     	mov	w1, #0x1                // =1
    c2fc: 91000a80     	add	x0, x20, #0x2
    c300: 94001e18     	bl	0x13b60 <calloc@plt>
    c304: b4000260     	cbz	x0, 0xc350 <.text+0x2350>
    c308: aa0003f3     	mov	x19, x0
    c30c: 90ffffc0     	adrp	x0, 0x4000
    c310: 91059000     	add	x0, x0, #0x164
    c314: aa1303e1     	mov	x1, x19
    c318: 2a1403e2     	mov	w2, w20
    c31c: 94001e1d     	bl	0x13b90 <upgrade_info_read_block@plt>
    c320: 340002c0     	cbz	w0, 0xc378 <.text+0x2378>
    c324: 90ffffe1     	adrp	x1, 0x8000
    c328: f0ffffa3     	adrp	x3, 0x3000
    c32c: 9115f821     	add	x1, x1, #0x57e
    c330: 910c4c63     	add	x3, x3, #0x313
    c334: 52803902     	mov	w2, #0x1c8              // =456
    c338: 2a1f03e0     	mov	w0, wzr
    c33c: 94001dc9     	bl	0x13a60 <up_logf@plt>
    c340: d0ffffc0     	adrp	x0, 0x6000
    c344: 9133b400     	add	x0, x0, #0xced
    c348: 94001dce     	bl	0x13a80 <upgrade_info_del_tag@plt>
    c34c: 14000020     	b	0xc3cc <.text+0x23cc>
    c350: 90ffffe1     	adrp	x1, 0x8000
    c354: 90ffffe3     	adrp	x3, 0x8000
    c358: 9115f821     	add	x1, x1, #0x57e
    c35c: 912e7c63     	add	x3, x3, #0xb9f
    c360: 52803842     	mov	w2, #0x1c2              // =450
    c364: 94001dbf     	bl	0x13a60 <up_logf@plt>
    c368: d0ffffc0     	adrp	x0, 0x6000
    c36c: 9133b400     	add	x0, x0, #0xced
    c370: 94001dc4     	bl	0x13a80 <upgrade_info_del_tag@plt>
    c374: 14000018     	b	0xc3d4 <.text+0x23d4>
    c378: d0ffffc0     	adrp	x0, 0x6000
    c37c: 9133b400     	add	x0, x0, #0xced
    c380: 94001dc0     	bl	0x13a80 <upgrade_info_del_tag@plt>
    c384: 340000e0     	cbz	w0, 0xc3a0 <.text+0x23a0>
    c388: 90ffffe1     	adrp	x1, 0x8000
    c38c: d0ffffa3     	adrp	x3, 0x2000
    c390: 9115f821     	add	x1, x1, #0x57e
    c394: 91242c63     	add	x3, x3, #0x90b
    c398: 52803a42     	mov	w2, #0x1d2              // =466
    c39c: 1400000a     	b	0xc3c4 <.text+0x23c4>
    c3a0: 90ffffc0     	adrp	x0, 0x4000
    c3a4: 91059000     	add	x0, x0, #0x164
    c3a8: 94001dfe     	bl	0x13ba0 <upgrade_info_del_block@plt>
    c3ac: 34000280     	cbz	w0, 0xc3fc <.text+0x23fc>
    c3b0: 90ffffe1     	adrp	x1, 0x8000
    c3b4: 90ffffc3     	adrp	x3, 0x4000
    c3b8: 9115f821     	add	x1, x1, #0x57e
    c3bc: 91258063     	add	x3, x3, #0x960
    c3c0: 52803b02     	mov	w2, #0x1d8              // =472
    c3c4: 2a1f03e0     	mov	w0, wzr
    c3c8: 94001da6     	bl	0x13a60 <up_logf@plt>
    c3cc: aa1303e0     	mov	x0, x19
    c3d0: 94001d24     	bl	0x13860 <free@plt>
    c3d4: 12800000     	mov	w0, #-0x1               // =-1
    c3d8: f94016a8     	ldr	x8, [x21, #0x28]
    c3dc: f94007e9     	ldr	x9, [sp, #0x8]
    c3e0: eb09011f     	cmp	x8, x9
    c3e4: 54000421     	b.ne	0xc468 <.text+0x2468>
    c3e8: a9434ff4     	ldp	x20, x19, [sp, #0x30]
    c3ec: f94013f5     	ldr	x21, [sp, #0x20]
    c3f0: a9417bfd     	ldp	x29, x30, [sp, #0x10]
    c3f4: 910103ff     	add	sp, sp, #0x40
    c3f8: d65f03c0     	ret
    c3fc: b94007e5     	ldr	w5, [sp, #0x4]
    c400: 90ffffe1     	adrp	x1, 0x8000
    c404: f0ffffc3     	adrp	x3, 0x7000
    c408: 9115f821     	add	x1, x1, #0x57e
    c40c: 913adc63     	add	x3, x3, #0xeb7
    c410: 52803ba2     	mov	w2, #0x1dd              // =477
    c414: aa1303e4     	mov	x4, x19
    c418: 94001d2a     	bl	0x138c0 <up_logi@plt>
    c41c: b94007e1     	ldr	w1, [sp, #0x4]
    c420: aa1303e0     	mov	x0, x19
    c424: 94001dd3     	bl	0x13b70 <up_hex_dump@plt>
    c428: aa1303e0     	mov	x0, x19
    c42c: 94001cf5     	bl	0x13800 <strlen@plt>
    c430: b0ffffc1     	adrp	x1, 0x5000
    c434: 8b000268     	add	x8, x19, x0
    c438: 5284c409     	mov	w9, #0x2620             // =9760
    c43c: 912a8421     	add	x1, x1, #0xaa1
    c440: aa1303e0     	mov	x0, x19
    c444: 79000109     	strh	w9, [x8]
    c448: 3900091f     	strb	wzr, [x8, #0x2]
    c44c: 94001dc1     	bl	0x13b50 <strstr@plt>
    c450: b4000060     	cbz	x0, 0xc45c <.text+0x245c>
    c454: aa1303e0     	mov	x0, x19
    c458: 94001dd6     	bl	0x13bb0 <system@plt>
    c45c: aa1303e0     	mov	x0, x19
    c460: 94001d00     	bl	0x13860 <free@plt>
    c464: 17ffffa2     	b	0xc2ec <.text+0x22ec>
    c468: 94001d0e     	bl	0x138a0 <__stack_chk_fail@plt>
