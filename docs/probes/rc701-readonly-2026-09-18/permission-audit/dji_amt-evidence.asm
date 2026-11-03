COMMAND: /opt/homebrew/opt/llvm/bin/llvm-objdump -d --start-address=16840 --stop-address=18400 /tmp/rm700_recon/dji_amt

/tmp/rm700_recon/dji_amt:	file format elf64-littleaarch64

Disassembly of section .text:

0000000000004000 <.text>:
    41c8: d10583ff     	sub	sp, sp, #0x160
    41cc: a9107bfd     	stp	x29, x30, [sp, #0x100]
    41d0: a9116ffc     	stp	x28, x27, [sp, #0x110]
    41d4: a91267fa     	stp	x26, x25, [sp, #0x120]
    41d8: a9135ff8     	stp	x24, x23, [sp, #0x130]
    41dc: a91457f6     	stp	x22, x21, [sp, #0x140]
    41e0: a9154ff4     	stp	x20, x19, [sp, #0x150]
    41e4: 910403fd     	add	x29, sp, #0x100
    41e8: d53bd05b     	mrs	x27, TPIDR_EL0
    41ec: f9401768     	ldr	x8, [x27, #0x28]
    41f0: 9100c3fc     	add	x28, sp, #0x30
    41f4: 6f00e400     	movi	v0.2d, #0000000000000000
    41f8: aa0103f3     	mov	x19, x1
    41fc: f81e83a8     	stur	x8, [x29, #-0x18]
    4200: f90017ff     	str	xzr, [sp, #0x28]
    4204: f9005b9f     	str	xzr, [x28, #0xb0]
    4208: ad048380     	stp	q0, q0, [x28, #0x90]
    420c: 3d802380     	str	q0, [x28, #0x80]
    4210: b90023ff     	str	wzr, [sp, #0x20]
    4214: b9401028     	ldr	w8, [x1, #0x10]
    4218: aa0003f8     	mov	x24, x0
    421c: 340003a8     	cbz	w8, 0x4290 <.text+0x290>
    4220: b9001fff     	str	wzr, [sp, #0x1c]
    4224: 94000fe9     	bl	0x81c8 <.text+0x41c8>
    4228: 7103c81f     	cmp	w0, #0xf2
    422c: 54000b41     	b.ne	0x4394 <.text+0x394>
    4230: 90000176     	adrp	x22, 0x30000
    4234: 394fc2c8     	ldrb	w8, [x22, #0x3f0]
    4238: 340006c8     	cbz	w8, 0x4310 <.text+0x310>
    423c: d0ffffe4     	adrp	x4, 0x2000
    4240: b0ffffe5     	adrp	x5, 0x1000
    4244: 6f00e400     	movi	v0.2d, #0000000000000000
    4248: 911de484     	add	x4, x4, #0x779
    424c: 912bb0a5     	add	x5, x5, #0xaec
    4250: 9100c3e2     	add	x2, sp, #0x30
    4254: 52800b40     	mov	w0, #0x5a               // =90
    4258: 52800021     	mov	w1, #0x1                // =1
    425c: 52801003     	mov	w3, #0x80               // =128
    4260: 52802b86     	mov	w6, #0x15c              // =348
    4264: 3d801f80     	str	q0, [x28, #0x70]
    4268: ad0403e0     	stp	q0, q0, [sp, #0x80]
    426c: ad0303e0     	stp	q0, q0, [sp, #0x60]
    4270: ad0203e0     	stp	q0, q0, [sp, #0x40]
    4274: 3d800fe0     	str	q0, [sp, #0x30]
    4278: 94001332     	bl	0x8f40 <duss_log_gen_head@plt>
    427c: 340008c0     	cbz	w0, 0x4394 <.text+0x394>
    4280: 394fc2c4     	ldrb	w4, [x22, #0x3f0]
    4284: b0ffffe3     	adrp	x3, 0x1000
    4288: 91393463     	add	x3, x3, #0xe4d
    428c: 1400003e     	b	0x4384 <.text+0x384>
    4290: d0ffffe4     	adrp	x4, 0x2000
    4294: b0ffffe5     	adrp	x5, 0x1000
    4298: 911de484     	add	x4, x4, #0x779
    429c: 912bb0a5     	add	x5, x5, #0xaec
    42a0: 9100c3e2     	add	x2, sp, #0x30
    42a4: 52800b40     	mov	w0, #0x5a               // =90
    42a8: 52800021     	mov	w1, #0x1                // =1
    42ac: 52801003     	mov	w3, #0x80               // =128
    42b0: 52802906     	mov	w6, #0x148              // =328
    42b4: 3d801f80     	str	q0, [x28, #0x70]
    42b8: ad0403e0     	stp	q0, q0, [sp, #0x80]
    42bc: ad0303e0     	stp	q0, q0, [sp, #0x60]
    42c0: ad0203e0     	stp	q0, q0, [sp, #0x40]
    42c4: 3d800fe0     	str	q0, [sp, #0x30]
    42c8: 9400131e     	bl	0x8f40 <duss_log_gen_head@plt>
    42cc: 34000120     	cbz	w0, 0x42f0 <.text+0x2f0>
    42d0: b9400264     	ldr	w4, [x19]
    42d4: b9401265     	ldr	w5, [x19, #0x10]
    42d8: d0ffffe3     	adrp	x3, 0x2000
    42dc: 91251463     	add	x3, x3, #0x945
    42e0: 9100c3e2     	add	x2, sp, #0x30
    42e4: 52800b40     	mov	w0, #0x5a               // =90
    42e8: 52800021     	mov	w1, #0x1                // =1
    42ec: 94001319     	bl	0x8f50 <duss_log_print@plt>
    42f0: 52801c82     	mov	w2, #0xe4               // =228
    42f4: aa1803e0     	mov	x0, x24
    42f8: aa1303e1     	mov	x1, x19
    42fc: aa1f03e3     	mov	x3, xzr
    4300: 2a1f03e4     	mov	w4, wzr
    4304: 94001317     	bl	0x8f60 <duss_event_resp_err@plt>
    4308: 12807d00     	mov	w0, #-0x3e9             // =-1001
    430c: 14000128     	b	0x47ac <.text+0x7ac>
    4310: 90000175     	adrp	x21, 0x30000
    4314: 910fc2b5     	add	x21, x21, #0x3f0
    4318: 910073e3     	add	x3, sp, #0x1c
    431c: 52800220     	mov	w0, #0x11               // =17
    4320: 52800022     	mov	w2, #0x1                // =1
    4324: aa1503e1     	mov	x1, x21
    4328: 94001312     	bl	0x8f70 <qsee_parameter_read@plt>
    432c: 394002a8     	ldrb	w8, [x21]
    4330: 34001ee8     	cbz	w8, 0x470c <.text+0x70c>
    4334: d0ffffe4     	adrp	x4, 0x2000
    4338: b0ffffe5     	adrp	x5, 0x1000
    433c: 6f00e400     	movi	v0.2d, #0000000000000000
    4340: 911de484     	add	x4, x4, #0x779
    4344: 912bb0a5     	add	x5, x5, #0xaec
    4348: 9100c3e2     	add	x2, sp, #0x30
    434c: 52800b40     	mov	w0, #0x5a               // =90
    4350: 52800021     	mov	w1, #0x1                // =1
    4354: 52801003     	mov	w3, #0x80               // =128
    4358: 52802b26     	mov	w6, #0x159              // =345
    435c: 3d801f80     	str	q0, [x28, #0x70]
    4360: ad0403e0     	stp	q0, q0, [sp, #0x80]
    4364: ad0303e0     	stp	q0, q0, [sp, #0x60]
    4368: ad0203e0     	stp	q0, q0, [sp, #0x40]
    436c: 3d800fe0     	str	q0, [sp, #0x30]
    4370: 940012f4     	bl	0x8f40 <duss_log_gen_head@plt>
    4374: 34000100     	cbz	w0, 0x4394 <.text+0x394>
    4378: 394fc2c4     	ldrb	w4, [x22, #0x3f0]
    437c: b0ffffe3     	adrp	x3, 0x1000
    4380: 913c6063     	add	x3, x3, #0xf18
    4384: 9100c3e2     	add	x2, sp, #0x30
    4388: 52800b40     	mov	w0, #0x5a               // =90
    438c: 52800021     	mov	w1, #0x1                // =1
    4390: 940012f0     	bl	0x8f50 <duss_log_print@plt>
    4394: 91005275     	add	x21, x19, #0x14
    4398: d0ffffe0     	adrp	x0, 0x2000
    439c: 91161400     	add	x0, x0, #0x585
    43a0: 528041c2     	mov	w2, #0x20e              // =526
    43a4: aa1503e1     	mov	x1, x21
    43a8: 97ffff28     	bl	0x4048 <.text+0x48>
    43ac: 90000056     	adrp	x22, 0xc000
    43b0: b94e6ac8     	ldr	w8, [x22, #0xe68]
    43b4: 7102011f     	cmp	w8, #0x80
    43b8: 54000123     	b.lo	0x43dc <.text+0x3dc>
    43bc: 52801ca2     	mov	w2, #0xe5               // =229
    43c0: aa1803e0     	mov	x0, x24
    43c4: aa1303e1     	mov	x1, x19
    43c8: aa1f03e3     	mov	x3, xzr
    43cc: 2a1f03e4     	mov	w4, wzr
    43d0: 940012e4     	bl	0x8f60 <duss_event_resp_err@plt>
    43d4: 12807dc0     	mov	w0, #-0x3ef             // =-1007
    43d8: 140000f5     	b	0x47ac <.text+0x7ac>
    43dc: b9421669     	ldr	w9, [x19, #0x214]
    43e0: f90007e9     	str	x9, [sp, #0x8]
    43e4: 79444269     	ldrh	w9, [x19, #0x220]
    43e8: f9000be9     	str	x9, [sp, #0x10]
    43ec: 340002c8     	cbz	w8, 0x4444 <.text+0x444>
    43f0: f90003fb     	str	x27, [sp]
    43f4: aa1803fb     	mov	x27, x24
    43f8: b9421a78     	ldr	w24, [x19, #0x218]
    43fc: b0000054     	adrp	x20, 0xd000
    4400: d0fffffa     	adrp	x26, 0x2000
    4404: aa1f03f7     	mov	x23, xzr
    4408: 9101b294     	add	x20, x20, #0x6c
    440c: 9106735a     	add	x26, x26, #0x19c
    4410: b9400282     	ldr	w2, [x20]
    4414: aa1a03e0     	mov	x0, x26
    4418: 2a1703e1     	mov	w1, w23
    441c: 940012b1     	bl	0x8ee0 <printf@plt>
    4420: b9400288     	ldr	w8, [x20]
    4424: 6b18011f     	cmp	w8, w24
    4428: 54000120     	b.eq	0x444c <.text+0x44c>
    442c: b94e6ad9     	ldr	w25, [x22, #0xe68]
    4430: 910006f7     	add	x23, x23, #0x1
    4434: 9111ae94     	add	x20, x20, #0x46b
    4438: eb1902ff     	cmp	x23, x25
    443c: 54fffea3     	b.lo	0x4410 <.text+0x410>
    4440: 14000006     	b	0x4458 <.text+0x458>
    4444: 2a1f03f9     	mov	w25, wzr
    4448: 14000006     	b	0x4460 <.text+0x460>
    444c: 7103ceff     	cmp	w23, #0xf3
    4450: 54001561     	b.ne	0x46fc <.text+0x6fc>
    4454: b94e6ad9     	ldr	w25, [x22, #0xe68]
    4458: aa1b03f8     	mov	x24, x27
    445c: f94003fb     	ldr	x27, [sp]
    4460: 11000728     	add	w8, w25, #0x1
    4464: b90e6ac8     	str	w8, [x22, #0xe68]
    4468: 90000056     	adrp	x22, 0xc000
    446c: 9139b2d6     	add	x22, x22, #0xe6c
    4470: 52808d68     	mov	w8, #0x46b              // =1131
    4474: 9b285b37     	smaddl	x23, w25, w8, x22
    4478: d0ffffe0     	adrp	x0, 0x2000
    447c: 91164c00     	add	x0, x0, #0x593
    4480: 2a1903e1     	mov	w1, w25
    4484: 2a1703e2     	mov	w2, w23
    4488: 94001296     	bl	0x8ee0 <printf@plt>
    448c: 910926fa     	add	x26, x23, #0x249
    4490: b9400348     	ldr	w8, [x26]
    4494: 7103fd1f     	cmp	w8, #0xff
    4498: 54000181     	b.ne	0x44c8 <.text+0x4c8>
    449c: b0ffffe0     	adrp	x0, 0x1000
    44a0: 913bc000     	add	x0, x0, #0xef0
    44a4: 94001287     	bl	0x8ec0 <puts@plt>
    44a8: 52801c82     	mov	w2, #0xe4               // =228
    44ac: aa1803e0     	mov	x0, x24
    44b0: aa1303e1     	mov	x1, x19
    44b4: aa1f03e3     	mov	x3, xzr
    44b8: 2a1f03e4     	mov	w4, wzr
    44bc: 940012a9     	bl	0x8f60 <duss_event_resp_err@plt>
    44c0: 12807d80     	mov	w0, #-0x3ed             // =-1005
    44c4: 140000ba     	b	0x47ac <.text+0x7ac>
    44c8: b9421e68     	ldr	w8, [x19, #0x21c]
    44cc: 34000d88     	cbz	w8, 0x467c <.text+0x67c>
    44d0: 93407f29     	sxtw	x9, w25
    44d4: 52808d6a     	mov	w10, #0x46b             // =1131
    44d8: 9b0a5934     	madd	x20, x9, x10, x22
    44dc: b9020688     	str	w8, [x20, #0x204]
    44e0: 79444268     	ldrh	w8, [x19, #0x220]
    44e4: 91094689     	add	x9, x20, #0x251
    44e8: f94007e2     	ldr	x2, [sp, #0x8]
    44ec: 6f00e400     	movi	v0.2d, #0000000000000000
    44f0: 79000128     	strh	w8, [x9]
    44f4: b9421a68     	ldr	w8, [x19, #0x218]
    44f8: 91119e89     	add	x9, x20, #0x467
    44fc: aa1703e0     	mov	x0, x23
    4500: aa1503e1     	mov	x1, x21
    4504: 91088a79     	add	x25, x19, #0x222
    4508: b9020288     	str	w8, [x20, #0x200]
    450c: b900013f     	str	wzr, [x9]
    4510: ad0f02e0     	stp	q0, q0, [x23, #0x1e0]
    4514: ad0e02e0     	stp	q0, q0, [x23, #0x1c0]
    4518: ad0d02e0     	stp	q0, q0, [x23, #0x1a0]
    451c: ad0c02e0     	stp	q0, q0, [x23, #0x180]
    4520: ad0b02e0     	stp	q0, q0, [x23, #0x160]
    4524: ad0a02e0     	stp	q0, q0, [x23, #0x140]
    4528: ad0902e0     	stp	q0, q0, [x23, #0x120]
    452c: ad0802e0     	stp	q0, q0, [x23, #0x100]
    4530: ad0702e0     	stp	q0, q0, [x23, #0xe0]
    4534: ad0602e0     	stp	q0, q0, [x23, #0xc0]
    4538: ad0502e0     	stp	q0, q0, [x23, #0xa0]
    453c: ad0402e0     	stp	q0, q0, [x23, #0x80]
    4540: ad0302e0     	stp	q0, q0, [x23, #0x60]
    4544: ad0202e0     	stp	q0, q0, [x23, #0x40]
    4548: ad0102e0     	stp	q0, q0, [x23, #0x20]
    454c: ad0002e0     	stp	q0, q0, [x23]
    4550: 9400128c     	bl	0x8f80 <memcpy@plt>
    4554: f9400be2     	ldr	x2, [sp, #0x10]
    4558: 91094e80     	add	x0, x20, #0x253
    455c: 6f00e400     	movi	v0.2d, #0000000000000000
    4560: aa1903e1     	mov	x1, x25
    4564: ad000000     	stp	q0, q0, [x0]
    4568: ad010000     	stp	q0, q0, [x0, #0x20]
    456c: ad020000     	stp	q0, q0, [x0, #0x40]
    4570: ad030000     	stp	q0, q0, [x0, #0x60]
    4574: ad040000     	stp	q0, q0, [x0, #0x80]
    4578: ad050000     	stp	q0, q0, [x0, #0xa0]
    457c: ad060000     	stp	q0, q0, [x0, #0xc0]
    4580: ad070000     	stp	q0, q0, [x0, #0xe0]
    4584: ad080000     	stp	q0, q0, [x0, #0x100]
    4588: ad090000     	stp	q0, q0, [x0, #0x120]
    458c: ad0a0000     	stp	q0, q0, [x0, #0x140]
    4590: ad0b0000     	stp	q0, q0, [x0, #0x160]
    4594: ad0c0000     	stp	q0, q0, [x0, #0x180]
    4598: ad0d0000     	stp	q0, q0, [x0, #0x1a0]
    459c: ad0e0000     	stp	q0, q0, [x0, #0x1c0]
    45a0: ad0f0000     	stp	q0, q0, [x0, #0x1e0]
    45a4: 94001277     	bl	0x8f80 <memcpy@plt>
    45a8: d10143a0     	sub	x0, x29, #0x50
    45ac: 94001279     	bl	0x8f90 <pthread_attr_init@plt>
    45b0: d10143a0     	sub	x0, x29, #0x50
    45b4: 52800021     	mov	w1, #0x1                // =1
    45b8: 9400127a     	bl	0x8fa0 <pthread_attr_setdetachstate@plt>
    45bc: d10143a0     	sub	x0, x29, #0x50
    45c0: 910083e1     	add	x1, sp, #0x20
    45c4: 9400127b     	bl	0x8fb0 <pthread_attr_getschedparam@plt>
    45c8: d10143a0     	sub	x0, x29, #0x50
    45cc: 910083e1     	add	x1, sp, #0x20
    45d0: b90023ff     	str	wzr, [sp, #0x20]
    45d4: 9400127b     	bl	0x8fc0 <pthread_attr_setschedparam@plt>
    45d8: d10143a0     	sub	x0, x29, #0x50
    45dc: 2a1f03e1     	mov	w1, wzr
    45e0: 9400127c     	bl	0x8fd0 <pthread_attr_setschedpolicy@plt>
    45e4: b0ffffe8     	adrp	x8, 0x1000
    45e8: fd43e500     	ldr	d0, [x8, #0x7c8]
    45ec: 90000002     	adrp	x2, 0x4000 <.text>
    45f0: 911f8042     	add	x2, x2, #0x7e0
    45f4: 9100a3e0     	add	x0, sp, #0x28
    45f8: d10143a1     	sub	x1, x29, #0x50
    45fc: aa1703e3     	mov	x3, x23
    4600: fd000340     	str	d0, [x26]
    4604: 94001277     	bl	0x8fe0 <pthread_create@plt>
    4608: 34000be0     	cbz	w0, 0x4784 <.text+0x784>
    460c: d0ffffe4     	adrp	x4, 0x2000
    4610: b0ffffe5     	adrp	x5, 0x1000
    4614: 6f00e400     	movi	v0.2d, #0000000000000000
    4618: 911de484     	add	x4, x4, #0x779
    461c: 912bb0a5     	add	x5, x5, #0xaec
    4620: 9100c3e2     	add	x2, sp, #0x30
    4624: 52800b40     	mov	w0, #0x5a               // =90
    4628: 52800021     	mov	w1, #0x1                // =1
    462c: 52801003     	mov	w3, #0x80               // =128
    4630: 52803566     	mov	w6, #0x1ab              // =427
    4634: 3d801f80     	str	q0, [x28, #0x70]
    4638: ad0403e0     	stp	q0, q0, [sp, #0x80]
    463c: ad0303e0     	stp	q0, q0, [sp, #0x60]
    4640: ad0203e0     	stp	q0, q0, [sp, #0x40]
    4644: 3d800fe0     	str	q0, [sp, #0x30]
    4648: 9400123e     	bl	0x8f40 <duss_log_gen_head@plt>
    464c: 340000e0     	cbz	w0, 0x4668 <.text+0x668>
    4650: b0ffffe3     	adrp	x3, 0x1000
    4654: 912ecc63     	add	x3, x3, #0xbb3
    4658: 9100c3e2     	add	x2, sp, #0x30
    465c: 52800b40     	mov	w0, #0x5a               // =90
    4660: 52800021     	mov	w1, #0x1                // =1
    4664: 9400123b     	bl	0x8f50 <duss_log_print@plt>
    4668: b0ffffe8     	adrp	x8, 0x1000
    466c: fd43e100     	ldr	d0, [x8, #0x7c0]
    4670: 52801c95     	mov	w21, #0xe4              // =228
    4674: fd000340     	str	d0, [x26]
    4678: 14000044     	b	0x4788 <.text+0x788>
    467c: d0ffffe4     	adrp	x4, 0x2000
    4680: b0ffffe5     	adrp	x5, 0x1000
    4684: 6f00e400     	movi	v0.2d, #0000000000000000
    4688: 911de484     	add	x4, x4, #0x779
    468c: 912bb0a5     	add	x5, x5, #0xaec
    4690: 9100c3e2     	add	x2, sp, #0x30
    4694: 52800b40     	mov	w0, #0x5a               // =90
    4698: 52800021     	mov	w1, #0x1                // =1
    469c: 52801003     	mov	w3, #0x80               // =128
    46a0: 52803106     	mov	w6, #0x188              // =392
    46a4: 3d801f80     	str	q0, [x28, #0x70]
    46a8: ad0403e0     	stp	q0, q0, [sp, #0x80]
    46ac: ad0303e0     	stp	q0, q0, [sp, #0x60]
    46b0: ad0203e0     	stp	q0, q0, [sp, #0x40]
    46b4: 3d800fe0     	str	q0, [sp, #0x30]
    46b8: 94001222     	bl	0x8f40 <duss_log_gen_head@plt>
    46bc: 34000100     	cbz	w0, 0x46dc <.text+0x6dc>
    46c0: d0ffffe3     	adrp	x3, 0x2000
    46c4: 9125b863     	add	x3, x3, #0x96e
    46c8: 9100c3e2     	add	x2, sp, #0x30
    46cc: 52800b40     	mov	w0, #0x5a               // =90
    46d0: 52800021     	mov	w1, #0x1                // =1
    46d4: aa1503e4     	mov	x4, x21
    46d8: 9400121e     	bl	0x8f50 <duss_log_print@plt>
    46dc: 52801cc2     	mov	w2, #0xe6               // =230
    46e0: aa1803e0     	mov	x0, x24
    46e4: aa1303e1     	mov	x1, x19
    46e8: aa1f03e3     	mov	x3, xzr
    46ec: 2a1f03e4     	mov	w4, wzr
    46f0: 9400121c     	bl	0x8f60 <duss_event_resp_err@plt>
    46f4: 12807d40     	mov	w0, #-0x3eb             // =-1003
    46f8: 1400002d     	b	0x47ac <.text+0x7ac>
    46fc: aa1b03f8     	mov	x24, x27
    4700: f94003fb     	ldr	x27, [sp]
    4704: 2a1703f9     	mov	w25, w23
    4708: 17ffff58     	b	0x4468 <.text+0x468>
    470c: d0ffffe4     	adrp	x4, 0x2000
    4710: b0ffffe5     	adrp	x5, 0x1000
    4714: 6f00e400     	movi	v0.2d, #0000000000000000
    4718: 911de484     	add	x4, x4, #0x779
    471c: 912bb0a5     	add	x5, x5, #0xaec
    4720: 9100c3e2     	add	x2, sp, #0x30
    4724: 52800b40     	mov	w0, #0x5a               // =90
    4728: 52800021     	mov	w1, #0x1                // =1
    472c: 52801003     	mov	w3, #0x80               // =128
    4730: 52802ac6     	mov	w6, #0x156              // =342
    4734: 3d801f80     	str	q0, [x28, #0x70]
    4738: ad0403e0     	stp	q0, q0, [sp, #0x80]
    473c: ad0303e0     	stp	q0, q0, [sp, #0x60]
    4740: ad0203e0     	stp	q0, q0, [sp, #0x40]
    4744: 3d800fe0     	str	q0, [sp, #0x30]
    4748: 940011fe     	bl	0x8f40 <duss_log_gen_head@plt>
    474c: 340000e0     	cbz	w0, 0x4768 <.text+0x768>
    4750: b0ffffe3     	adrp	x3, 0x1000
    4754: 912bfc63     	add	x3, x3, #0xaff
    4758: 9100c3e2     	add	x2, sp, #0x30
    475c: 52800b40     	mov	w0, #0x5a               // =90
    4760: 52800021     	mov	w1, #0x1                // =1
    4764: 940011fb     	bl	0x8f50 <duss_log_print@plt>
    4768: 52801c82     	mov	w2, #0xe4               // =228
    476c: aa1803e0     	mov	x0, x24
    4770: aa1303e1     	mov	x1, x19
    4774: aa1f03e3     	mov	x3, xzr
    4778: 2a1f03e4     	mov	w4, wzr
    477c: 940011f9     	bl	0x8f60 <duss_event_resp_err@plt>
    4780: 1400000b     	b	0x47ac <.text+0x7ac>
    4784: 2a1f03f5     	mov	w21, wzr
    4788: d10143a0     	sub	x0, x29, #0x50
    478c: 94001219     	bl	0x8ff0 <pthread_attr_destroy@plt>
    4790: aa1803e0     	mov	x0, x24
    4794: aa1303e1     	mov	x1, x19
    4798: 2a1503e2     	mov	w2, w21
    479c: aa1f03e3     	mov	x3, xzr
    47a0: 2a1f03e4     	mov	w4, wzr
    47a4: 940011ef     	bl	0x8f60 <duss_event_resp_err@plt>
    47a8: 2a1f03e0     	mov	w0, wzr
    47ac: f9401768     	ldr	x8, [x27, #0x28]
    47b0: f85e83a9     	ldur	x9, [x29, #-0x18]
    47b4: eb09011f     	cmp	x8, x9
    47b8: 54000121     	b.ne	0x47dc <.text+0x7dc>
    47bc: a9554ff4     	ldp	x20, x19, [sp, #0x150]
    47c0: a95457f6     	ldp	x22, x21, [sp, #0x140]
    47c4: a9535ff8     	ldp	x24, x23, [sp, #0x130]
    47c8: a95267fa     	ldp	x26, x25, [sp, #0x120]
    47cc: a9516ffc     	ldp	x28, x27, [sp, #0x110]
    47d0: a9507bfd     	ldp	x29, x30, [sp, #0x100]
    47d4: 910583ff     	add	sp, sp, #0x160
    47d8: d65f03c0     	ret
    47dc: 94001209     	bl	0x9000 <__stack_chk_fail@plt>

COMMAND: /opt/homebrew/opt/llvm/bin/llvm-objdump -d --start-address=18400 --stop-address=20868 /tmp/rm700_recon/dji_amt

/tmp/rm700_recon/dji_amt:	file format elf64-littleaarch64

Disassembly of section .text:

0000000000004000 <.text>:
    47e0: a9ba7bfd     	stp	x29, x30, [sp, #-0x60]!
    47e4: a9016ffc     	stp	x28, x27, [sp, #0x10]
    47e8: a90267fa     	stp	x26, x25, [sp, #0x20]
    47ec: a9035ff8     	stp	x24, x23, [sp, #0x30]
    47f0: a90457f6     	stp	x22, x21, [sp, #0x40]
    47f4: a9054ff4     	stp	x20, x19, [sp, #0x50]
    47f8: 910003fd     	mov	x29, sp
    47fc: d11303ff     	sub	sp, sp, #0x4c0
    4800: d53bd05a     	mrs	x26, TPIDR_EL0
    4804: f9401748     	ldr	x8, [x26, #0x28]
    4808: b0ffffe9     	adrp	x9, 0x1000
    480c: fd43e521     	ldr	d1, [x9, #0x7c8]
    4810: 6f00e400     	movi	v0.2d, #0000000000000000
    4814: f81f03a8     	stur	x8, [x29, #-0x10]
    4818: b9420414     	ldr	w20, [x0, #0x204]
    481c: 9108e416     	add	x22, x0, #0x239
    4820: aa0003f3     	mov	x19, x0
    4824: 3d812be0     	str	q0, [sp, #0x4a0]
    4828: 3d8127e0     	str	q0, [sp, #0x490]
    482c: 3d8123e0     	str	q0, [sp, #0x480]
    4830: 3d811fe0     	str	q0, [sp, #0x470]
    4834: 3d811be0     	str	q0, [sp, #0x460]
    4838: 3d8117e0     	str	q0, [sp, #0x450]
    483c: 3d8113e0     	str	q0, [sp, #0x440]
    4840: 3d810fe0     	str	q0, [sp, #0x430]
    4844: 3d810be0     	str	q0, [sp, #0x420]
    4848: 3d8107e0     	str	q0, [sp, #0x410]
    484c: ad1f83e0     	stp	q0, q0, [sp, #0x3f0]
    4850: ad1e83e0     	stp	q0, q0, [sp, #0x3d0]
    4854: ad1d83e0     	stp	q0, q0, [sp, #0x3b0]
    4858: ad1c83e0     	stp	q0, q0, [sp, #0x390]
    485c: ad1b83e0     	stp	q0, q0, [sp, #0x370]
    4860: ad1a83e0     	stp	q0, q0, [sp, #0x350]
    4864: ad1983e0     	stp	q0, q0, [sp, #0x330]
    4868: ad1883e0     	stp	q0, q0, [sp, #0x310]
    486c: ad1783e0     	stp	q0, q0, [sp, #0x2f0]
    4870: ad1683e0     	stp	q0, q0, [sp, #0x2d0]
    4874: ad1583e0     	stp	q0, q0, [sp, #0x2b0]
    4878: ad1483e0     	stp	q0, q0, [sp, #0x290]
    487c: ad1383e0     	stp	q0, q0, [sp, #0x270]
    4880: ad1283e0     	stp	q0, q0, [sp, #0x250]
    4884: ad1183e0     	stp	q0, q0, [sp, #0x230]
    4888: ad1083e0     	stp	q0, q0, [sp, #0x210]
    488c: ad0f83e0     	stp	q0, q0, [sp, #0x1f0]
    4890: ad0e83e0     	stp	q0, q0, [sp, #0x1d0]
    4894: ad0d83e0     	stp	q0, q0, [sp, #0x1b0]
    4898: ad0c83e0     	stp	q0, q0, [sp, #0x190]
    489c: ad0b83e0     	stp	q0, q0, [sp, #0x170]
    48a0: ad0a83e0     	stp	q0, q0, [sp, #0x150]
    48a4: ad0983e0     	stp	q0, q0, [sp, #0x130]
    48a8: ad0883e0     	stp	q0, q0, [sp, #0x110]
    48ac: ad0783e0     	stp	q0, q0, [sp, #0xf0]
    48b0: ad0683e0     	stp	q0, q0, [sp, #0xd0]
    48b4: ad0583e0     	stp	q0, q0, [sp, #0xb0]
    48b8: fd000ac1     	str	d1, [x22, #0x10]
    48bc: 9400118d     	bl	0x8ef0 <pthread_self@plt>
    48c0: f90002c0     	str	x0, [x22]
    48c4: 3948e268     	ldrb	w8, [x19, #0x238]
    48c8: 34000348     	cbz	w8, 0x4930 <.text+0x930>
    48cc: f9410660     	ldr	x0, [x19, #0x208]
    48d0: 940011d0     	bl	0x9010 <duss_osal_timer_restart@plt>
    48d4: 9108aac8     	add	x8, x22, #0x22a
    48d8: b900011f     	str	wzr, [x8]
    48dc: 91086ad7     	add	x23, x22, #0x21a
    48e0: f94002e0     	ldr	x0, [x23]
    48e4: 52990002     	mov	w2, #0xc800             // =51200
    48e8: 2a1f03e1     	mov	w1, wzr
    48ec: f90013e8     	str	x8, [sp, #0x20]
    48f0: 94001188     	bl	0x8f10 <memset@plt>
    48f4: f94002f4     	ldr	x20, [x23]
    48f8: 910ac3e0     	add	x0, sp, #0x2b0
    48fc: aa1303e3     	mov	x3, x19
    4900: 94000c50     	bl	0x7a40 <.text+0x3a40>
    4904: b0ffffe0     	adrp	x0, 0x1000
    4908: 911ff800     	add	x0, x0, #0x7fe
    490c: aa1303e1     	mov	x1, x19
    4910: 94001174     	bl	0x8ee0 <printf@plt>
    4914: 794032c2     	ldrh	w2, [x22, #0x18]
    4918: b4000d02     	cbz	x2, 0x4ab8 <.text+0xab8>
    491c: 7100205f     	cmp	w2, #0x8
    4920: 91094e61     	add	x1, x19, #0x253
    4924: 540005c2     	b.hs	0x49dc <.text+0x9dc>
    4928: aa1f03e8     	mov	x8, xzr
    492c: 1400016c     	b	0x4edc <.text+0xedc>
    4930: b0ffffe8     	adrp	x8, 0x1000
    4934: 91334d08     	add	x8, x8, #0xcd3
    4938: b0000009     	adrp	x9, 0x5000 <.text+0x1000>
    493c: 52807d0a     	mov	w10, #0x3e8             // =1000
    4940: 91084260     	add	x0, x19, #0x210
    4944: 910e2129     	add	x9, x9, #0x388
    4948: f9010a68     	str	x8, [x19, #0x210]
    494c: 1b0a7e88     	mul	w8, w20, w10
    4950: 91082261     	add	x1, x19, #0x208
    4954: f9011273     	str	x19, [x19, #0x220]
    4958: f9010e69     	str	x9, [x19, #0x218]
    495c: b9022a68     	str	w8, [x19, #0x228]
    4960: b902327f     	str	wzr, [x19, #0x230]
    4964: b902367f     	str	wzr, [x19, #0x234]
    4968: 940011ae     	bl	0x9020 <duss_osal_timer_create@plt>
    496c: 34fffb40     	cbz	w0, 0x48d4 <.text+0x8d4>
    4970: d0ffffe4     	adrp	x4, 0x2000
    4974: d0ffffe5     	adrp	x5, 0x2000
    4978: 2a0003f4     	mov	w20, w0
    497c: 52801e28     	mov	w8, #0xf1               // =241
    4980: 6f00e400     	movi	v0.2d, #0000000000000000
    4984: 911de484     	add	x4, x4, #0x779
    4988: 911b7ca5     	add	x5, x5, #0x6df
    498c: 9100c3e2     	add	x2, sp, #0x30
    4990: 52800b40     	mov	w0, #0x5a               // =90
    4994: 52800021     	mov	w1, #0x1                // =1
    4998: 52801003     	mov	w3, #0x80               // =128
    499c: 52801506     	mov	w6, #0xa8               // =168
    49a0: b90016c8     	str	w8, [x22, #0x14]
    49a4: ad0483e0     	stp	q0, q0, [sp, #0x90]
    49a8: ad0383e0     	stp	q0, q0, [sp, #0x70]
    49ac: ad0283e0     	stp	q0, q0, [sp, #0x50]
    49b0: ad0183e0     	stp	q0, q0, [sp, #0x30]
    49b4: 94001163     	bl	0x8f40 <duss_log_gen_head@plt>
    49b8: 34fff8e0     	cbz	w0, 0x48d4 <.text+0x8d4>
    49bc: d0ffffe3     	adrp	x3, 0x2000
    49c0: 9112a463     	add	x3, x3, #0x4a9
    49c4: 9100c3e2     	add	x2, sp, #0x30
    49c8: 52800b40     	mov	w0, #0x5a               // =90
    49cc: 52800021     	mov	w1, #0x1                // =1
    49d0: 2a1403e4     	mov	w4, w20
    49d4: 9400115f     	bl	0x8f50 <duss_log_print@plt>
    49d8: 17ffffbf     	b	0x48d4 <.text+0x8d4>
    49dc: 927d3048     	and	x8, x2, #0xfff8
    49e0: 52800409     	mov	w9, #0x20               // =32
    49e4: aa0103ea     	mov	x10, x1
    49e8: aa0803eb     	mov	x11, x8
    49ec: 14000004     	b	0x49fc <.text+0x9fc>
    49f0: f100216b     	subs	x11, x11, #0x8
    49f4: 9100214a     	add	x10, x10, #0x8
    49f8: 54000560     	b.eq	0x4aa4 <.text+0xaa4>
    49fc: fd400140     	ldr	d0, [x10]
    4a00: 0e209800     	cmeq	v0.8b, v0.8b, #0
    4a04: 0e013c0c     	umov	w12, v0.b[0]
    4a08: 3700020c     	tbnz	w12, #0x0, 0x4a48 <.text+0xa48>
    4a0c: 0e033c0c     	umov	w12, v0.b[1]
    4a10: 3700022c     	tbnz	w12, #0x0, 0x4a54 <.text+0xa54>
    4a14: 0e053c0c     	umov	w12, v0.b[2]
    4a18: 3700024c     	tbnz	w12, #0x0, 0x4a60 <.text+0xa60>
    4a1c: 0e073c0c     	umov	w12, v0.b[3]
    4a20: 3700026c     	tbnz	w12, #0x0, 0x4a6c <.text+0xa6c>
    4a24: 0e093c0c     	umov	w12, v0.b[4]
    4a28: 3700028c     	tbnz	w12, #0x0, 0x4a78 <.text+0xa78>
    4a2c: 0e0b3c0c     	umov	w12, v0.b[5]
    4a30: 370002ac     	tbnz	w12, #0x0, 0x4a84 <.text+0xa84>
    4a34: 0e0d3c0c     	umov	w12, v0.b[6]
    4a38: 370002cc     	tbnz	w12, #0x0, 0x4a90 <.text+0xa90>
    4a3c: 0e0f3c0c     	umov	w12, v0.b[7]
    4a40: 3607fd8c     	tbz	w12, #0x0, 0x49f0 <.text+0x9f0>
    4a44: 14000016     	b	0x4a9c <.text+0xa9c>
    4a48: 39000149     	strb	w9, [x10]
    4a4c: 0e033c0c     	umov	w12, v0.b[1]
    4a50: 3607fe2c     	tbz	w12, #0x0, 0x4a14 <.text+0xa14>
    4a54: 39000549     	strb	w9, [x10, #0x1]
    4a58: 0e053c0c     	umov	w12, v0.b[2]
    4a5c: 3607fe0c     	tbz	w12, #0x0, 0x4a1c <.text+0xa1c>
    4a60: 39000949     	strb	w9, [x10, #0x2]
    4a64: 0e073c0c     	umov	w12, v0.b[3]
    4a68: 3607fdec     	tbz	w12, #0x0, 0x4a24 <.text+0xa24>
    4a6c: 39000d49     	strb	w9, [x10, #0x3]
    4a70: 0e093c0c     	umov	w12, v0.b[4]
    4a74: 3607fdcc     	tbz	w12, #0x0, 0x4a2c <.text+0xa2c>
    4a78: 39001149     	strb	w9, [x10, #0x4]
    4a7c: 0e0b3c0c     	umov	w12, v0.b[5]
    4a80: 3607fdac     	tbz	w12, #0x0, 0x4a34 <.text+0xa34>
    4a84: 39001549     	strb	w9, [x10, #0x5]
    4a88: 0e0d3c0c     	umov	w12, v0.b[6]
    4a8c: 3607fd8c     	tbz	w12, #0x0, 0x4a3c <.text+0xa3c>
    4a90: 39001949     	strb	w9, [x10, #0x6]
    4a94: 0e0f3c0c     	umov	w12, v0.b[7]
    4a98: 3607facc     	tbz	w12, #0x0, 0x49f0 <.text+0x9f0>
    4a9c: 39001d49     	strb	w9, [x10, #0x7]
    4aa0: 17ffffd4     	b	0x49f0 <.text+0x9f0>
    4aa4: eb02011f     	cmp	x8, x2
    4aa8: 540021a1     	b.ne	0x4edc <.text+0xedc>
    4aac: 910ac3e0     	add	x0, sp, #0x2b0
    4ab0: 52804003     	mov	w3, #0x200              // =512
    4ab4: 9400115f     	bl	0x9030 <__strncat_chk@plt>
    4ab8: d0ffffe4     	adrp	x4, 0x2000
    4abc: d0ffffe5     	adrp	x5, 0x2000
    4ac0: 91088ac8     	add	x8, x22, #0x222
    4ac4: 6f00e400     	movi	v0.2d, #0000000000000000
    4ac8: 911de484     	add	x4, x4, #0x779
    4acc: 911b7ca5     	add	x5, x5, #0x6df
    4ad0: 9100c3e2     	add	x2, sp, #0x30
    4ad4: 52800b40     	mov	w0, #0x5a               // =90
    4ad8: 52800061     	mov	w1, #0x3                // =3
    4adc: 52801003     	mov	w3, #0x80               // =128
    4ae0: 52801846     	mov	w6, #0xc2               // =194
    4ae4: f900011f     	str	xzr, [x8]
    4ae8: ad0483e0     	stp	q0, q0, [sp, #0x90]
    4aec: ad0383e0     	stp	q0, q0, [sp, #0x70]
    4af0: ad0283e0     	stp	q0, q0, [sp, #0x50]
    4af4: ad0183e0     	stp	q0, q0, [sp, #0x30]
    4af8: 94001112     	bl	0x8f40 <duss_log_gen_head@plt>
    4afc: 34000100     	cbz	w0, 0x4b1c <.text+0xb1c>
    4b00: d0ffffe3     	adrp	x3, 0x2000
    4b04: 91180863     	add	x3, x3, #0x602
    4b08: 9100c3e2     	add	x2, sp, #0x30
    4b0c: 910ac3e4     	add	x4, sp, #0x2b0
    4b10: 52800b40     	mov	w0, #0x5a               // =90
    4b14: 52800061     	mov	w1, #0x3                // =3
    4b18: 9400110e     	bl	0x8f50 <duss_log_print@plt>
    4b1c: d0ffffe1     	adrp	x1, 0x2000
    4b20: 91129c21     	add	x1, x1, #0x4a7
    4b24: 910ac3e0     	add	x0, sp, #0x2b0
    4b28: 94001146     	bl	0x9040 <popen@plt>
    4b2c: b4001f00     	cbz	x0, 0x4f0c <.text+0xf0c>
    4b30: d0ffffe4     	adrp	x4, 0x2000
    4b34: d0ffffe5     	adrp	x5, 0x2000
    4b38: aa0003f5     	mov	x21, x0
    4b3c: 6f00e400     	movi	v0.2d, #0000000000000000
    4b40: 911de484     	add	x4, x4, #0x779
    4b44: 911b7ca5     	add	x5, x5, #0x6df
    4b48: 9100c3e2     	add	x2, sp, #0x30
    4b4c: 52800b40     	mov	w0, #0x5a               // =90
    4b50: 52800061     	mov	w1, #0x3                // =3
    4b54: 52801003     	mov	w3, #0x80               // =128
    4b58: 52801946     	mov	w6, #0xca               // =202
    4b5c: f9000bf7     	str	x23, [sp, #0x10]
    4b60: ad0483e0     	stp	q0, q0, [sp, #0x90]
    4b64: ad0383e0     	stp	q0, q0, [sp, #0x70]
    4b68: ad0283e0     	stp	q0, q0, [sp, #0x50]
    4b6c: ad0183e0     	stp	q0, q0, [sp, #0x30]
    4b70: 940010f4     	bl	0x8f40 <duss_log_gen_head@plt>
    4b74: 34000120     	cbz	w0, 0x4b98 <.text+0xb98>
    4b78: 9108bac8     	add	x8, x22, #0x22e
    4b7c: b9400104     	ldr	w4, [x8]
    4b80: d0ffffe3     	adrp	x3, 0x2000
    4b84: 912d7063     	add	x3, x3, #0xb5c
    4b88: 9100c3e2     	add	x2, sp, #0x30
    4b8c: 52800b40     	mov	w0, #0x5a               // =90
    4b90: 52800061     	mov	w1, #0x3                // =3
    4b94: 940010ef     	bl	0x8f50 <duss_log_print@plt>
    4b98: f9000ff6     	str	x22, [sp, #0x18]
    4b9c: 9108badc     	add	x28, x22, #0x22e
    4ba0: d0fffff6     	adrp	x22, 0x2000
    4ba4: d0fffff7     	adrp	x23, 0x2000
    4ba8: d0fffff8     	adrp	x24, 0x2000
    4bac: b0fffff9     	adrp	x25, 0x1000
    4bb0: 5299001b     	mov	w27, #0xc800            // =51200
    4bb4: 911de6d6     	add	x22, x22, #0x779
    4bb8: 911b7ef7     	add	x23, x23, #0x6df
    4bbc: 911bf718     	add	x24, x24, #0x6fd
    4bc0: 912f3b39     	add	x25, x25, #0xbce
    4bc4: b9002fff     	str	wzr, [sp, #0x2c]
    4bc8: aa1403e0     	mov	x0, x20
    4bcc: 2a1b03e1     	mov	w1, w27
    4bd0: aa1503e2     	mov	x2, x21
    4bd4: 9400111f     	bl	0x9050 <fgets@plt>
    4bd8: b4001100     	cbz	x0, 0x4df8 <.text+0xdf8>
    4bdc: b9400388     	ldr	w8, [x28]
    4be0: 7100051f     	cmp	w8, #0x1
    4be4: 54000541     	b.ne	0x4c8c <.text+0xc8c>
    4be8: 6f00e400     	movi	v0.2d, #0000000000000000
    4bec: 9100c3e2     	add	x2, sp, #0x30
    4bf0: 52800b40     	mov	w0, #0x5a               // =90
    4bf4: 52800061     	mov	w1, #0x3                // =3
    4bf8: 52801003     	mov	w3, #0x80               // =128
    4bfc: 528019a6     	mov	w6, #0xcd               // =205
    4c00: aa1603e4     	mov	x4, x22
    4c04: aa1703e5     	mov	x5, x23
    4c08: ad0483e0     	stp	q0, q0, [sp, #0x90]
    4c0c: ad0383e0     	stp	q0, q0, [sp, #0x70]
    4c10: ad0283e0     	stp	q0, q0, [sp, #0x50]
    4c14: ad0183e0     	stp	q0, q0, [sp, #0x30]
    4c18: 940010ca     	bl	0x8f40 <duss_log_gen_head@plt>
    4c1c: 340000e0     	cbz	w0, 0x4c38 <.text+0xc38>
    4c20: 9100c3e2     	add	x2, sp, #0x30
    4c24: 52800b40     	mov	w0, #0x5a               // =90
    4c28: 52800061     	mov	w1, #0x3                // =3
    4c2c: aa1903e3     	mov	x3, x25
    4c30: aa1403e4     	mov	x4, x20
    4c34: 940010c7     	bl	0x8f50 <duss_log_print@plt>
    4c38: 6f00e400     	movi	v0.2d, #0000000000000000
    4c3c: 9100c3e2     	add	x2, sp, #0x30
    4c40: 52800b40     	mov	w0, #0x5a               // =90
    4c44: 52800061     	mov	w1, #0x3                // =3
    4c48: 52801003     	mov	w3, #0x80               // =128
    4c4c: 528019c6     	mov	w6, #0xce               // =206
    4c50: aa1603e4     	mov	x4, x22
    4c54: aa1703e5     	mov	x5, x23
    4c58: ad0483e0     	stp	q0, q0, [sp, #0x90]
    4c5c: ad0383e0     	stp	q0, q0, [sp, #0x70]
    4c60: ad0283e0     	stp	q0, q0, [sp, #0x50]
    4c64: ad0183e0     	stp	q0, q0, [sp, #0x30]
    4c68: 940010b6     	bl	0x8f40 <duss_log_gen_head@plt>
    4c6c: 34fffae0     	cbz	w0, 0x4bc8 <.text+0xbc8>
    4c70: 9100c3e2     	add	x2, sp, #0x30
    4c74: 52800b40     	mov	w0, #0x5a               // =90
    4c78: 52800061     	mov	w1, #0x3                // =3
    4c7c: aa1803e3     	mov	x3, x24
    4c80: aa1403e4     	mov	x4, x20
    4c84: 940010b3     	bl	0x8f50 <duss_log_print@plt>
    4c88: 17ffffd0     	b	0x4bc8 <.text+0xbc8>
    4c8c: aa1403e0     	mov	x0, x20
    4c90: a9006bf3     	stp	x19, x26, [sp]
    4c94: 940010f3     	bl	0x9060 <strlen@plt>
    4c98: f94013e9     	ldr	x9, [sp, #0x20]
    4c9c: b9400128     	ldr	w8, [x9]
    4ca0: 0b000108     	add	w8, w8, w0
    4ca4: aa1403e0     	mov	x0, x20
    4ca8: b9000128     	str	w8, [x9]
    4cac: 940010ed     	bl	0x9060 <strlen@plt>
    4cb0: aa0003fa     	mov	x26, x0
    4cb4: aa1403e0     	mov	x0, x20
    4cb8: 940010ea     	bl	0x9060 <strlen@plt>
    4cbc: f9400fe8     	ldr	x8, [sp, #0x18]
    4cc0: b9402ff3     	ldr	w19, [sp, #0x2c]
    4cc4: aa1403e2     	mov	x2, x20
    4cc8: b9400101     	ldr	w1, [x8]
    4ccc: 0b000273     	add	w19, w19, w0
    4cd0: d0ffffe0     	adrp	x0, 0x2000
    4cd4: 910e3800     	add	x0, x0, #0x38e
    4cd8: 94001082     	bl	0x8ee0 <printf@plt>
    4cdc: 52988028     	mov	w8, #0xc401             // =50177
    4ce0: 6b08027f     	cmp	w19, w8
    4ce4: b9002ff3     	str	w19, [sp, #0x2c]
    4ce8: 5400036b     	b.lt	0x4d54 <.text+0xd54>
    4cec: b0ffffe0     	adrp	x0, 0x1000
    4cf0: 52808841     	mov	w1, #0x442              // =1090
    4cf4: 528036c2     	mov	w2, #0x1b6              // =438
    4cf8: 913dcc00     	add	x0, x0, #0xf73
    4cfc: 940010dd     	bl	0x9070 <open@plt>
    4d00: 37f80360     	tbnz	w0, #0x1f, 0x4d6c <.text+0xd6c>
    4d04: 2a0003f4     	mov	w20, w0
    4d08: 910ac3e0     	add	x0, sp, #0x2b0
    4d0c: 52804001     	mov	w1, #0x200              // =512
    4d10: 940010dc     	bl	0x9080 <__strlen_chk@plt>
    4d14: aa0003e2     	mov	x2, x0
    4d18: 910ac3e1     	add	x1, sp, #0x2b0
    4d1c: 52804003     	mov	w3, #0x200              // =512
    4d20: 2a1403e0     	mov	w0, w20
    4d24: 940010db     	bl	0x9090 <__write_chk@plt>
    4d28: f9400bfb     	ldr	x27, [sp, #0x10]
    4d2c: b9402fe8     	ldr	w8, [sp, #0x2c]
    4d30: 92800003     	mov	x3, #-0x1               // =-1
    4d34: 2a1403e0     	mov	w0, w20
    4d38: f9400361     	ldr	x1, [x27]
    4d3c: 2a0803e2     	mov	w2, w8
    4d40: 940010d4     	bl	0x9090 <__write_chk@plt>
    4d44: 2a1403e0     	mov	w0, w20
    4d48: 940010d6     	bl	0x90a0 <close@plt>
    4d4c: a9406bf3     	ldp	x19, x26, [sp]
    4d50: 14000020     	b	0x4dd0 <.text+0xdd0>
    4d54: aa1403e0     	mov	x0, x20
    4d58: 4b1a037b     	sub	w27, w27, w26
    4d5c: 940010c1     	bl	0x9060 <strlen@plt>
    4d60: a9406bf3     	ldp	x19, x26, [sp]
    4d64: 8b000294     	add	x20, x20, x0
    4d68: 1400001d     	b	0x4ddc <.text+0xddc>
    4d6c: 940010d1     	bl	0x90b0 <__errno@plt>
    4d70: b9400014     	ldr	w20, [x0]
    4d74: 6f00e400     	movi	v0.2d, #0000000000000000
    4d78: 9100c3e2     	add	x2, sp, #0x30
    4d7c: 52800b40     	mov	w0, #0x5a               // =90
    4d80: 52800021     	mov	w1, #0x1                // =1
    4d84: 52801003     	mov	w3, #0x80               // =128
    4d88: 52801c06     	mov	w6, #0xe0               // =224
    4d8c: aa1603e4     	mov	x4, x22
    4d90: aa1703e5     	mov	x5, x23
    4d94: ad0483e0     	stp	q0, q0, [sp, #0x90]
    4d98: ad0383e0     	stp	q0, q0, [sp, #0x70]
    4d9c: ad0283e0     	stp	q0, q0, [sp, #0x50]
    4da0: ad0183e0     	stp	q0, q0, [sp, #0x30]
    4da4: 94001067     	bl	0x8f40 <duss_log_gen_head@plt>
    4da8: a940effa     	ldp	x26, x27, [sp, #0x8]
    4dac: f94003f3     	ldr	x19, [sp]
    4db0: 34000100     	cbz	w0, 0x4dd0 <.text+0xdd0>
    4db4: d0ffffe3     	adrp	x3, 0x2000
    4db8: 9100c3e2     	add	x2, sp, #0x30
    4dbc: 52800b40     	mov	w0, #0x5a               // =90
    4dc0: 52800021     	mov	w1, #0x1                // =1
    4dc4: 9122d063     	add	x3, x3, #0x8b4
    4dc8: 2a1403e4     	mov	w4, w20
    4dcc: 94001061     	bl	0x8f50 <duss_log_print@plt>
    4dd0: f9400374     	ldr	x20, [x27]
    4dd4: b9002fff     	str	wzr, [sp, #0x2c]
    4dd8: 5299001b     	mov	w27, #0xc800            // =51200
    4ddc: f94013e8     	ldr	x8, [sp, #0x20]
    4de0: b0ffffe0     	adrp	x0, 0x1000
    4de4: 912a1400     	add	x0, x0, #0xa85
    4de8: 2a1b03e2     	mov	w2, w27
    4dec: b9400101     	ldr	w1, [x8]
    4df0: 9400103c     	bl	0x8ee0 <printf@plt>
    4df4: 17ffff75     	b	0x4bc8 <.text+0xbc8>
    4df8: d0ffffe0     	adrp	x0, 0x2000
    4dfc: 9110ac00     	add	x0, x0, #0x42b
    4e00: 910ac3e1     	add	x1, sp, #0x2b0
    4e04: 94001037     	bl	0x8ee0 <printf@plt>
    4e08: f9410660     	ldr	x0, [x19, #0x208]
    4e0c: 940010ad     	bl	0x90c0 <duss_osal_timer_suspend@plt>
    4e10: d0ffffe4     	adrp	x4, 0x2000
    4e14: d0ffffe5     	adrp	x5, 0x2000
    4e18: 6f00e400     	movi	v0.2d, #0000000000000000
    4e1c: 911de484     	add	x4, x4, #0x779
    4e20: 911b7ca5     	add	x5, x5, #0x6df
    4e24: 9100c3e2     	add	x2, sp, #0x30
    4e28: 52800b40     	mov	w0, #0x5a               // =90
    4e2c: 52800061     	mov	w1, #0x3                // =3
    4e30: 52801003     	mov	w3, #0x80               // =128
    4e34: 52801e86     	mov	w6, #0xf4               // =244
    4e38: ad0483e0     	stp	q0, q0, [sp, #0x90]
    4e3c: ad0383e0     	stp	q0, q0, [sp, #0x70]
    4e40: ad0283e0     	stp	q0, q0, [sp, #0x50]
    4e44: ad0183e0     	stp	q0, q0, [sp, #0x30]
    4e48: 9400103e     	bl	0x8f40 <duss_log_gen_head@plt>
    4e4c: 34000120     	cbz	w0, 0x4e70 <.text+0xe70>
    4e50: f9400be8     	ldr	x8, [sp, #0x10]
    4e54: d0ffffe3     	adrp	x3, 0x2000
    4e58: 91183c63     	add	x3, x3, #0x60f
    4e5c: 9100c3e2     	add	x2, sp, #0x30
    4e60: f9400104     	ldr	x4, [x8]
    4e64: 52800b40     	mov	w0, #0x5a               // =90
    4e68: 52800061     	mov	w1, #0x3                // =3
    4e6c: 94001039     	bl	0x8f50 <duss_log_print@plt>
    4e70: aa1503e0     	mov	x0, x21
    4e74: 94001097     	bl	0x90d0 <pclose@plt>
    4e78: f9400ff6     	ldr	x22, [sp, #0x18]
    4e7c: 3100041f     	cmn	w0, #0x1
    4e80: 54000c40     	b.eq	0x5008 <.text+0x1008>
    4e84: 7200181f     	tst	w0, #0x7f
    4e88: 54000f61     	b.ne	0x5074 <.text+0x1074>
    4e8c: 53083c14     	ubfx	w20, w0, #8, #8
    4e90: 6f00e400     	movi	v0.2d, #0000000000000000
    4e94: ad1483e0     	stp	q0, q0, [sp, #0x290]
    4e98: ad1383e0     	stp	q0, q0, [sp, #0x270]
    4e9c: ad1283e0     	stp	q0, q0, [sp, #0x250]
    4ea0: ad1183e0     	stp	q0, q0, [sp, #0x230]
    4ea4: ad1083e0     	stp	q0, q0, [sp, #0x210]
    4ea8: ad0f83e0     	stp	q0, q0, [sp, #0x1f0]
    4eac: ad0e83e0     	stp	q0, q0, [sp, #0x1d0]
    4eb0: ad0d83e0     	stp	q0, q0, [sp, #0x1b0]
    4eb4: ad0c83e0     	stp	q0, q0, [sp, #0x190]
    4eb8: ad0b83e0     	stp	q0, q0, [sp, #0x170]
    4ebc: ad0a83e0     	stp	q0, q0, [sp, #0x150]
    4ec0: ad0983e0     	stp	q0, q0, [sp, #0x130]
    4ec4: ad0883e0     	stp	q0, q0, [sp, #0x110]
    4ec8: ad0783e0     	stp	q0, q0, [sp, #0xf0]
    4ecc: ad0683e0     	stp	q0, q0, [sp, #0xd0]
    4ed0: ad0583e0     	stp	q0, q0, [sp, #0xb0]
    4ed4: 350004d4     	cbnz	w20, 0x4f6c <.text+0xf6c>
    4ed8: 14000078     	b	0x50b8 <.text+0x10b8>
    4edc: 8b130109     	add	x9, x8, x19
    4ee0: cb080048     	sub	x8, x2, x8
    4ee4: 91094d29     	add	x9, x9, #0x253
    4ee8: 5280040a     	mov	w10, #0x20              // =32
    4eec: 14000004     	b	0x4efc <.text+0xefc>
    4ef0: f1000508     	subs	x8, x8, #0x1
    4ef4: 91000529     	add	x9, x9, #0x1
    4ef8: 54ffdda0     	b.eq	0x4aac <.text+0xaac>
    4efc: 3940012b     	ldrb	w11, [x9]
    4f00: 35ffff8b     	cbnz	w11, 0x4ef0 <.text+0xef0>
    4f04: 3900012a     	strb	w10, [x9]
    4f08: 17fffffa     	b	0x4ef0 <.text+0xef0>
    4f0c: d0ffffe0     	adrp	x0, 0x2000
    4f10: 9110ac00     	add	x0, x0, #0x42b
    4f14: 910ac3e1     	add	x1, sp, #0x2b0
    4f18: 94000ff2     	bl	0x8ee0 <printf@plt>
    4f1c: 52801e48     	mov	w8, #0xf2               // =242
    4f20: 6f00e400     	movi	v0.2d, #0000000000000000
    4f24: b90016c8     	str	w8, [x22, #0x14]
    4f28: ad0583e0     	stp	q0, q0, [sp, #0xb0]
    4f2c: ad0683e0     	stp	q0, q0, [sp, #0xd0]
    4f30: ad0783e0     	stp	q0, q0, [sp, #0xf0]
    4f34: ad0883e0     	stp	q0, q0, [sp, #0x110]
    4f38: ad0983e0     	stp	q0, q0, [sp, #0x130]
    4f3c: ad0a83e0     	stp	q0, q0, [sp, #0x150]
    4f40: ad0b83e0     	stp	q0, q0, [sp, #0x170]
    4f44: ad0c83e0     	stp	q0, q0, [sp, #0x190]
    4f48: ad0d83e0     	stp	q0, q0, [sp, #0x1b0]
    4f4c: ad0e83e0     	stp	q0, q0, [sp, #0x1d0]
    4f50: ad0f83e0     	stp	q0, q0, [sp, #0x1f0]
    4f54: ad1083e0     	stp	q0, q0, [sp, #0x210]
    4f58: ad1183e0     	stp	q0, q0, [sp, #0x230]
    4f5c: ad1283e0     	stp	q0, q0, [sp, #0x250]
    4f60: ad1383e0     	stp	q0, q0, [sp, #0x270]
    4f64: ad1483e0     	stp	q0, q0, [sp, #0x290]
    4f68: 12800014     	mov	w20, #-0x1              // =-1
    4f6c: d0ffffe4     	adrp	x4, 0x2000
    4f70: d0ffffe5     	adrp	x5, 0x2000
    4f74: 6f00e400     	movi	v0.2d, #0000000000000000
    4f78: 911de484     	add	x4, x4, #0x779
    4f7c: 911b7ca5     	add	x5, x5, #0x6df
    4f80: 9100c3e2     	add	x2, sp, #0x30
    4f84: 52800b40     	mov	w0, #0x5a               // =90
    4f88: 52800021     	mov	w1, #0x1                // =1
    4f8c: 52801003     	mov	w3, #0x80               // =128
    4f90: 52802066     	mov	w6, #0x103              // =259
    4f94: ad0483e0     	stp	q0, q0, [sp, #0x90]
    4f98: ad0383e0     	stp	q0, q0, [sp, #0x70]
    4f9c: ad0283e0     	stp	q0, q0, [sp, #0x50]
    4fa0: ad0183e0     	stp	q0, q0, [sp, #0x30]
    4fa4: 94000fe7     	bl	0x8f40 <duss_log_gen_head@plt>
    4fa8: 34000100     	cbz	w0, 0x4fc8 <.text+0xfc8>
    4fac: b0ffffe3     	adrp	x3, 0x1000
    4fb0: 9135e463     	add	x3, x3, #0xd79
    4fb4: 9100c3e2     	add	x2, sp, #0x30
    4fb8: 52800b40     	mov	w0, #0x5a               // =90
    4fbc: 52800021     	mov	w1, #0x1                // =1
    4fc0: aa1303e4     	mov	x4, x19
    4fc4: 94000fe3     	bl	0x8f50 <duss_log_print@plt>
    4fc8: b0ffffe0     	adrp	x0, 0x1000
    4fcc: 9135e400     	add	x0, x0, #0xd79
    4fd0: aa1303e1     	mov	x1, x19
    4fd4: 94000fc3     	bl	0x8ee0 <printf@plt>
    4fd8: 9108bac8     	add	x8, x22, #0x22e
    4fdc: b9400108     	ldr	w8, [x8]
    4fe0: d0ffffe3     	adrp	x3, 0x2000
    4fe4: 91188863     	add	x3, x3, #0x622
    4fe8: 9102c3e0     	add	x0, sp, #0xb0
    4fec: 7100051f     	cmp	w8, #0x1
    4ff0: 52801ec8     	mov	w8, #0xf6               // =246
    4ff4: 1a880288     	csel	w8, w20, w8, eq
    4ff8: aa1303e4     	mov	x4, x19
    4ffc: 2a1403e5     	mov	w5, w20
    5000: b90016c8     	str	w8, [x22, #0x14]
    5004: 14000033     	b	0x50d0 <.text+0x10d0>
    5008: 9400102a     	bl	0x90b0 <__errno@plt>
    500c: b9400014     	ldr	w20, [x0]
    5010: b0ffffe4     	adrp	x4, 0x2000
    5014: b0ffffe5     	adrp	x5, 0x2000
    5018: 52801e48     	mov	w8, #0xf2               // =242
    501c: 6f00e400     	movi	v0.2d, #0000000000000000
    5020: 911de484     	add	x4, x4, #0x779
    5024: 911b7ca5     	add	x5, x5, #0x6df
    5028: 9100c3e2     	add	x2, sp, #0x30
    502c: 52800b40     	mov	w0, #0x5a               // =90
    5030: 52800021     	mov	w1, #0x1                // =1
    5034: 52801003     	mov	w3, #0x80               // =128
    5038: 52801f26     	mov	w6, #0xf9               // =249
    503c: b90016c8     	str	w8, [x22, #0x14]
    5040: ad0483e0     	stp	q0, q0, [sp, #0x90]
    5044: ad0383e0     	stp	q0, q0, [sp, #0x70]
    5048: ad0283e0     	stp	q0, q0, [sp, #0x50]
    504c: ad0183e0     	stp	q0, q0, [sp, #0x30]
    5050: 94000fbc     	bl	0x8f40 <duss_log_gen_head@plt>
    5054: 34000100     	cbz	w0, 0x5074 <.text+0x1074>
    5058: 90ffffe3     	adrp	x3, 0x1000
    505c: 912ab863     	add	x3, x3, #0xaae
    5060: 9100c3e2     	add	x2, sp, #0x30
    5064: 52800b40     	mov	w0, #0x5a               // =90
    5068: 52800021     	mov	w1, #0x1                // =1
    506c: 2a1403e4     	mov	w4, w20
    5070: 94000fb8     	bl	0x8f50 <duss_log_print@plt>
    5074: 6f00e400     	movi	v0.2d, #0000000000000000
    5078: ad1483e0     	stp	q0, q0, [sp, #0x290]
    507c: ad1383e0     	stp	q0, q0, [sp, #0x270]
    5080: ad1283e0     	stp	q0, q0, [sp, #0x250]
    5084: ad1183e0     	stp	q0, q0, [sp, #0x230]
    5088: ad1083e0     	stp	q0, q0, [sp, #0x210]
    508c: ad0f83e0     	stp	q0, q0, [sp, #0x1f0]
    5090: ad0e83e0     	stp	q0, q0, [sp, #0x1d0]
    5094: ad0d83e0     	stp	q0, q0, [sp, #0x1b0]
    5098: ad0c83e0     	stp	q0, q0, [sp, #0x190]
    509c: ad0b83e0     	stp	q0, q0, [sp, #0x170]
    50a0: ad0a83e0     	stp	q0, q0, [sp, #0x150]
    50a4: ad0983e0     	stp	q0, q0, [sp, #0x130]
    50a8: ad0883e0     	stp	q0, q0, [sp, #0x110]
    50ac: ad0783e0     	stp	q0, q0, [sp, #0xf0]
    50b0: ad0683e0     	stp	q0, q0, [sp, #0xd0]
    50b4: ad0583e0     	stp	q0, q0, [sp, #0xb0]
    50b8: b0ffffe3     	adrp	x3, 0x2000
    50bc: b90016df     	str	wzr, [x22, #0x14]
    50c0: 910e8863     	add	x3, x3, #0x3a2
    50c4: 9102c3e0     	add	x0, sp, #0xb0
    50c8: aa1303e4     	mov	x4, x19
    50cc: 2a1f03e5     	mov	w5, wzr
    50d0: 94000a87     	bl	0x7aec <.text+0x3aec>
    50d4: b0ffffe4     	adrp	x4, 0x2000
    50d8: b0ffffe5     	adrp	x5, 0x2000
    50dc: 6f00e400     	movi	v0.2d, #0000000000000000
    50e0: 911de484     	add	x4, x4, #0x779
    50e4: 911b7ca5     	add	x5, x5, #0x6df
    50e8: 9100c3e2     	add	x2, sp, #0x30
    50ec: 52800b40     	mov	w0, #0x5a               // =90
    50f0: 52800061     	mov	w1, #0x3                // =3
    50f4: 52801003     	mov	w3, #0x80               // =128
    50f8: 52802206     	mov	w6, #0x110              // =272
    50fc: ad0483e0     	stp	q0, q0, [sp, #0x90]
    5100: ad0383e0     	stp	q0, q0, [sp, #0x70]
    5104: ad0283e0     	stp	q0, q0, [sp, #0x50]
    5108: ad0183e0     	stp	q0, q0, [sp, #0x30]
    510c: 94000f8d     	bl	0x8f40 <duss_log_gen_head@plt>
    5110: 34000100     	cbz	w0, 0x5130 <.text+0x1130>
    5114: b0ffffe3     	adrp	x3, 0x2000
    5118: 912ae463     	add	x3, x3, #0xab9
    511c: 9100c3e2     	add	x2, sp, #0x30
    5120: 9102c3e4     	add	x4, sp, #0xb0
    5124: 52800b40     	mov	w0, #0x5a               // =90
    5128: 52800061     	mov	w1, #0x3                // =3
    512c: 94000f89     	bl	0x8f50 <duss_log_print@plt>
    5130: f9410660     	ldr	x0, [x19, #0x208]
    5134: 94000feb     	bl	0x90e0 <duss_osal_timer_destroy@plt>
    5138: b0ffffe0     	adrp	x0, 0x2000
    513c: 91012c00     	add	x0, x0, #0x4b
    5140: 3908e27f     	strb	wzr, [x19, #0x238]
    5144: 94000f5f     	bl	0x8ec0 <puts@plt>
    5148: 52801fc8     	mov	w8, #0xfe               // =254
    514c: b90012c8     	str	w8, [x22, #0x10]
    5150: f9401748     	ldr	x8, [x26, #0x28]
    5154: f85f03a9     	ldur	x9, [x29, #-0x10]
    5158: eb09011f     	cmp	x8, x9
    515c: 54000121     	b.ne	0x5180 <.text+0x1180>
    5160: 911303ff     	add	sp, sp, #0x4c0
    5164: a9454ff4     	ldp	x20, x19, [sp, #0x50]
    5168: a94457f6     	ldp	x22, x21, [sp, #0x40]
    516c: a9435ff8     	ldp	x24, x23, [sp, #0x30]
    5170: a94267fa     	ldp	x26, x25, [sp, #0x20]
    5174: a9416ffc     	ldp	x28, x27, [sp, #0x10]
    5178: a8c67bfd     	ldp	x29, x30, [sp], #0x60
    517c: d65f03c0     	ret
    5180: 94000fa0     	bl	0x9000 <__stack_chk_fail@plt>

COMMAND: /opt/homebrew/opt/llvm/bin/llvm-objdump -d --start-address=28952 --stop-address=28960 /tmp/rm700_recon/dji_amt

/tmp/rm700_recon/dji_amt:	file format elf64-littleaarch64

Disassembly of section .text:

0000000000004000 <.text>:
    7118: 2a1f03e0     	mov	w0, wzr
    711c: d65f03c0     	ret

COMMAND: /opt/homebrew/opt/llvm/bin/llvm-objdump -d --start-address=30996 --stop-address=31296 /tmp/rm700_recon/dji_amt

/tmp/rm700_recon/dji_amt:	file format elf64-littleaarch64

Disassembly of section .text:

0000000000004000 <.text>:
    7914: a9bc7bfd     	stp	x29, x30, [sp, #-0x40]!
    7918: f9000bfc     	str	x28, [sp, #0x10]
    791c: a90257f6     	stp	x22, x21, [sp, #0x20]
    7920: a9034ff4     	stp	x20, x19, [sp, #0x30]
    7924: 910003fd     	mov	x29, sp
    7928: d11283ff     	sub	sp, sp, #0x4a0
    792c: d53bd055     	mrs	x21, TPIDR_EL0
    7930: f94016a8     	ldr	x8, [x21, #0x28]
    7934: aa0103f3     	mov	x19, x1
    7938: aa0003f4     	mov	x20, x0
    793c: 910253e0     	add	x0, sp, #0x94
    7940: 52808082     	mov	w2, #0x404              // =1028
    7944: 2a1f03e1     	mov	w1, wzr
    7948: f81f83a8     	stur	x8, [x29, #-0x8]
    794c: 52808096     	mov	w22, #0x404             // =1028
    7950: 94000570     	bl	0x8f10 <memset@plt>
    7954: f0ffffc4     	adrp	x4, 0x2000
    7958: f0ffffc5     	adrp	x5, 0x2000
    795c: 6f00e400     	movi	v0.2d, #0000000000000000
    7960: 911de484     	add	x4, x4, #0x779
    7964: 911b38a5     	add	x5, x5, #0x6ce
    7968: 910043e2     	add	x2, sp, #0x10
    796c: 52800b40     	mov	w0, #0x5a               // =90
    7970: 52800061     	mov	w1, #0x3                // =3
    7974: 52801003     	mov	w3, #0x80               // =128
    7978: 5280c1a6     	mov	w6, #0x60d              // =1549
    797c: b9000ff6     	str	w22, [sp, #0xc]
    7980: ad0383e0     	stp	q0, q0, [sp, #0x70]
    7984: ad0283e0     	stp	q0, q0, [sp, #0x50]
    7988: ad0183e0     	stp	q0, q0, [sp, #0x30]
    798c: ad0083e0     	stp	q0, q0, [sp, #0x10]
    7990: 9400056c     	bl	0x8f40 <duss_log_gen_head@plt>
    7994: 34000100     	cbz	w0, 0x79b4 <.text+0x39b4>
    7998: 79400e64     	ldrh	w4, [x19, #0x6]
    799c: f0ffffc3     	adrp	x3, 0x2000
    79a0: 91103063     	add	x3, x3, #0x40c
    79a4: 910043e2     	add	x2, sp, #0x10
    79a8: 52800b40     	mov	w0, #0x5a               // =90
    79ac: 52800061     	mov	w1, #0x3                // =3
    79b0: 94000568     	bl	0x8f50 <duss_log_print@plt>
    79b4: b9401261     	ldr	w1, [x19, #0x10]
    79b8: 91005260     	add	x0, x19, #0x14
    79bc: 910253e2     	add	x2, sp, #0x94
    79c0: 910033e3     	add	x3, sp, #0xc
    79c4: 94000442     	bl	0x8acc <.text+0x4acc>
    79c8: b9400fe3     	ldr	w3, [sp, #0xc]
    79cc: 910253e2     	add	x2, sp, #0x94
    79d0: aa1403e0     	mov	x0, x20
    79d4: aa1303e1     	mov	x1, x19
    79d8: 940005fe     	bl	0x91d0 <duss_event_resp_data_without_retcode@plt>
    79dc: f94016a8     	ldr	x8, [x21, #0x28]
    79e0: f85f83a9     	ldur	x9, [x29, #-0x8]
    79e4: eb09011f     	cmp	x8, x9
    79e8: 540000e1     	b.ne	0x7a04 <.text+0x3a04>
    79ec: 911283ff     	add	sp, sp, #0x4a0
    79f0: a9434ff4     	ldp	x20, x19, [sp, #0x30]
    79f4: a94257f6     	ldp	x22, x21, [sp, #0x20]
    79f8: f9400bfc     	ldr	x28, [sp, #0x10]
    79fc: a8c47bfd     	ldp	x29, x30, [sp], #0x40
    7a00: d65f03c0     	ret
    7a04: 9400057f     	bl	0x9000 <__stack_chk_fail@plt>
    7a08: a9be7bfd     	stp	x29, x30, [sp, #-0x20]!
    7a0c: f9000bf3     	str	x19, [sp, #0x10]
    7a10: 910003fd     	mov	x29, sp
    7a14: 2a0003f3     	mov	w19, w0
    7a18: b0000140     	adrp	x0, 0x30000
    7a1c: 91108000     	add	x0, x0, #0x420
    7a20: 94000427     	bl	0x8abc <.text+0x4abc>
    7a24: 90000020     	adrp	x0, 0xb000
    7a28: 911ba000     	add	x0, x0, #0x6e8
    7a2c: 9400041c     	bl	0x8a9c <.text+0x4a9c>
    7a30: 2a1303e0     	mov	w0, w19
    7a34: f9400bf3     	ldr	x19, [sp, #0x10]
    7a38: a8c27bfd     	ldp	x29, x30, [sp], #0x20
    7a3c: 1400041c     	b	0x8aac <.text+0x4aac>

COMMAND: /opt/homebrew/opt/llvm/bin/llvm-objdump -d --start-address=33224 --stop-address=34768 /tmp/rm700_recon/dji_amt

/tmp/rm700_recon/dji_amt:	file format elf64-littleaarch64

Disassembly of section .text:

0000000000004000 <.text>:
    81c8: a9bf7bfd     	stp	x29, x30, [sp, #-0x10]!
    81cc: 910003fd     	mov	x29, sp
    81d0: d0ffffc0     	adrp	x0, 0x2000
    81d4: 9109b800     	add	x0, x0, #0x26e
    81d8: 94000412     	bl	0x9220 <system@plt>
    81dc: 340001a0     	cbz	w0, 0x8210 <.text+0x4210>
    81e0: d0ffffc0     	adrp	x0, 0x2000
    81e4: 912da800     	add	x0, x0, #0xb6a
    81e8: 9400040e     	bl	0x9220 <system@plt>
    81ec: 34000180     	cbz	w0, 0x821c <.text+0x421c>
    81f0: d0ffffc0     	adrp	x0, 0x2000
    81f4: 911d1000     	add	x0, x0, #0x744
    81f8: 9400040a     	bl	0x9220 <system@plt>
    81fc: 7100001f     	cmp	w0, #0x0
    8200: 52801e48     	mov	w8, #0xf2               // =242
    8204: 1a880500     	cinc	w0, w8, ne
    8208: a8c17bfd     	ldp	x29, x30, [sp], #0x10
    820c: d65f03c0     	ret
    8210: 52801e00     	mov	w0, #0xf0               // =240
    8214: a8c17bfd     	ldp	x29, x30, [sp], #0x10
    8218: d65f03c0     	ret
    821c: 52801e20     	mov	w0, #0xf1               // =241
    8220: a8c17bfd     	ldp	x29, x30, [sp], #0x10
    8224: d65f03c0     	ret
    8228: d106c3ff     	sub	sp, sp, #0x1b0
    822c: a9187bfd     	stp	x29, x30, [sp, #0x180]
    8230: f900cbfc     	str	x28, [sp, #0x190]
    8234: a91a4ff4     	stp	x20, x19, [sp, #0x1a0]
    8238: 910603fd     	add	x29, sp, #0x180
    823c: 910203e8     	add	x8, sp, #0x80
    8240: 6f00e400     	movi	v0.2d, #0000000000000000
    8244: 91004100     	add	x0, x8, #0x10
    8248: f900bbff     	str	xzr, [sp, #0x170]
    824c: ad0a83e0     	stp	q0, q0, [sp, #0x150]
    8250: ad0983e0     	stp	q0, q0, [sp, #0x130]
    8254: ad0883e0     	stp	q0, q0, [sp, #0x110]
    8258: ad0783e0     	stp	q0, q0, [sp, #0xf0]
    825c: ad0683e0     	stp	q0, q0, [sp, #0xd0]
    8260: ad0583e0     	stp	q0, q0, [sp, #0xb0]
    8264: ad0483e0     	stp	q0, q0, [sp, #0x90]
    8268: 3d8023e0     	str	q0, [sp, #0x80]
    826c: 940003f1     	bl	0x9230 <sigemptyset@plt>
    8270: b90083ff     	str	wzr, [sp, #0x80]
    8274: d0000008     	adrp	x8, 0xa000
    8278: f9415108     	ldr	x8, [x8, #0x2a0]
    827c: 910203e1     	add	x1, sp, #0x80
    8280: 528001c0     	mov	w0, #0xe                // =14
    8284: aa1f03e2     	mov	x2, xzr
    8288: f90047e8     	str	x8, [sp, #0x88]
    828c: 940003ed     	bl	0x9240 <sigaction@plt>
    8290: 97ffef9f     	bl	0x410c <.text+0x10c>
    8294: 52800700     	mov	w0, #0x38               // =56
    8298: 52800021     	mov	w1, #0x1                // =1
    829c: 52800034     	mov	w20, #0x1               // =1
    82a0: 940003ec     	bl	0x9250 <calloc@plt>
    82a4: d0ffffc4     	adrp	x4, 0x2000
    82a8: d0ffffc5     	adrp	x5, 0x2000
    82ac: aa0003f3     	mov	x19, x0
    82b0: 6f00e400     	movi	v0.2d, #0000000000000000
    82b4: 91245084     	add	x4, x4, #0x914
    82b8: 911900a5     	add	x5, x5, #0x640
    82bc: 910003e2     	mov	x2, sp
    82c0: 52800b40     	mov	w0, #0x5a               // =90
    82c4: 52800061     	mov	w1, #0x3                // =3
    82c8: 52801003     	mov	w3, #0x80               // =128
    82cc: 52800f66     	mov	w6, #0x7b               // =123
    82d0: ad0303e0     	stp	q0, q0, [sp, #0x60]
    82d4: ad0203e0     	stp	q0, q0, [sp, #0x40]
    82d8: ad0103e0     	stp	q0, q0, [sp, #0x20]
    82dc: ad0003e0     	stp	q0, q0, [sp]
    82e0: 94000318     	bl	0x8f40 <duss_log_gen_head@plt>
    82e4: 34000100     	cbz	w0, 0x8304 <.text+0x4304>
    82e8: d0ffffc3     	adrp	x3, 0x2000
    82ec: 91076063     	add	x3, x3, #0x1d8
    82f0: 910003e2     	mov	x2, sp
    82f4: 52800b40     	mov	w0, #0x5a               // =90
    82f8: 52800061     	mov	w1, #0x3                // =3
    82fc: 52800044     	mov	w4, #0x2                // =2
    8300: 94000314     	bl	0x8f50 <duss_log_print@plt>
    8304: 6f00e400     	movi	v0.2d, #0000000000000000
    8308: b0ffffc8     	adrp	x8, 0x1000
    830c: ad0503e0     	stp	q0, q0, [sp, #0xa0]
    8310: ad0603e0     	stp	q0, q0, [sp, #0xc0]
    8314: ad0703e0     	stp	q0, q0, [sp, #0xe0]
    8318: ad0803e0     	stp	q0, q0, [sp, #0x100]
    831c: ad0903e0     	stp	q0, q0, [sp, #0x120]
    8320: ad0a03e0     	stp	q0, q0, [sp, #0x140]
    8324: 3d805be0     	str	q0, [sp, #0x160]
    8328: fd43d900     	ldr	d0, [x8, #0x7b0]
    832c: 52802008     	mov	w8, #0x100              // =256
    8330: f900bbff     	str	xzr, [sp, #0x170]
    8334: b900abe8     	str	w8, [sp, #0xa8]
    8338: 390573f4     	strb	w20, [sp, #0x15c]
    833c: fd0053e0     	str	d0, [sp, #0xa0]
    8340: 940003c8     	bl	0x9260 <duss_sketch_get_config_file_name@plt>
    8344: f900a7e0     	str	x0, [sp, #0x148]
    8348: 910283e0     	add	x0, sp, #0xa0
    834c: 940003c9     	bl	0x9270 <duss_sketch_initialize@plt>
    8350: 350004a0     	cbnz	w0, 0x83e4 <.text+0x43e4>
    8354: d0ffffc4     	adrp	x4, 0x2000
    8358: d0ffffc5     	adrp	x5, 0x2000
    835c: 6f00e400     	movi	v0.2d, #0000000000000000
    8360: 91245084     	add	x4, x4, #0x914
    8364: 911900a5     	add	x5, x5, #0x640
    8368: 910003e2     	mov	x2, sp
    836c: 52800b40     	mov	w0, #0x5a               // =90
    8370: 52800061     	mov	w1, #0x3                // =3
    8374: 52801003     	mov	w3, #0x80               // =128
    8378: 52801106     	mov	w6, #0x88               // =136
    837c: ad0303e0     	stp	q0, q0, [sp, #0x60]
    8380: ad0203e0     	stp	q0, q0, [sp, #0x40]
    8384: ad0103e0     	stp	q0, q0, [sp, #0x20]
    8388: ad0003e0     	stp	q0, q0, [sp]
    838c: 940002ed     	bl	0x8f40 <duss_log_gen_head@plt>
    8390: 34000160     	cbz	w0, 0x83bc <.text+0x43bc>
    8394: d0ffffc3     	adrp	x3, 0x2000
    8398: d0ffffc4     	adrp	x4, 0x2000
    839c: d0ffffc5     	adrp	x5, 0x2000
    83a0: 91155063     	add	x3, x3, #0x554
    83a4: 9102b084     	add	x4, x4, #0xac
    83a8: 910804a5     	add	x5, x5, #0x201
    83ac: 910003e2     	mov	x2, sp
    83b0: 52800b40     	mov	w0, #0x5a               // =90
    83b4: 52800061     	mov	w1, #0x3                // =3
    83b8: 940002e6     	bl	0x8f50 <duss_log_print@plt>
    83bc: f0000008     	adrp	x8, 0xb000
    83c0: 52801fe9     	mov	w9, #0xff               // =255
    83c4: 52801fe0     	mov	w0, #0xff               // =255
    83c8: b9070109     	str	w9, [x8, #0x700]
    83cc: 97fffd8f     	bl	0x7a08 <.text+0x3a08>
    83d0: aa1303e0     	mov	x0, x19
    83d4: 9400001e     	bl	0x844c <.text+0x444c>
    83d8: 52800140     	mov	w0, #0xa                // =10
    83dc: 940003a9     	bl	0x9280 <sleep@plt>
    83e0: 17fffffe     	b	0x83d8 <.text+0x43d8>
    83e4: d0ffffc4     	adrp	x4, 0x2000
    83e8: d0ffffc5     	adrp	x5, 0x2000
    83ec: 2a0003f3     	mov	w19, w0
    83f0: 6f00e400     	movi	v0.2d, #0000000000000000
    83f4: 91245084     	add	x4, x4, #0x914
    83f8: 911900a5     	add	x5, x5, #0x640
    83fc: 910003e2     	mov	x2, sp
    8400: 52800b40     	mov	w0, #0x5a               // =90
    8404: 52800021     	mov	w1, #0x1                // =1
    8408: 52801003     	mov	w3, #0x80               // =128
    840c: 52801086     	mov	w6, #0x84               // =132
    8410: ad0303e0     	stp	q0, q0, [sp, #0x60]
    8414: ad0203e0     	stp	q0, q0, [sp, #0x40]
    8418: ad0103e0     	stp	q0, q0, [sp, #0x20]
    841c: ad0003e0     	stp	q0, q0, [sp]
    8420: 940002c8     	bl	0x8f40 <duss_log_gen_head@plt>
    8424: 34000100     	cbz	w0, 0x8444 <.text+0x4444>
    8428: b0ffffc3     	adrp	x3, 0x1000
    842c: 91350463     	add	x3, x3, #0xd41
    8430: 910003e2     	mov	x2, sp
    8434: 52800b40     	mov	w0, #0x5a               // =90
    8438: 52800021     	mov	w1, #0x1                // =1
    843c: 2a1303e4     	mov	w4, w19
    8440: 940002c4     	bl	0x8f50 <duss_log_print@plt>
    8444: 12800000     	mov	w0, #-0x1               // =-1
    8448: 94000392     	bl	0x9290 <exit@plt>
    844c: a9bd7bfd     	stp	x29, x30, [sp, #-0x30]!
    8450: a90157fc     	stp	x28, x21, [sp, #0x10]
    8454: a9024ff4     	stp	x20, x19, [sp, #0x20]
    8458: 910003fd     	mov	x29, sp
    845c: d14007ff     	sub	sp, sp, #0x1, lsl #12   // =0x1000
    8460: d10f83ff     	sub	sp, sp, #0x3e0
    8464: d53bd055     	mrs	x21, TPIDR_EL0
    8468: f94016a8     	ldr	x8, [x21, #0x28]
    846c: aa0003f3     	mov	x19, x0
    8470: 912f23e0     	add	x0, sp, #0xbc8
    8474: 52810202     	mov	w2, #0x810              // =2064
    8478: 2a1f03e1     	mov	w1, wzr
    847c: 911143f4     	add	x20, sp, #0x450
    8480: f81f83a8     	stur	x8, [x29, #-0x8]
    8484: 79001bff     	strh	wzr, [sp, #0xc]
    8488: 940002a2     	bl	0x8f10 <memset@plt>
    848c: 91002280     	add	x0, x20, #0x8
    8490: 5280ee02     	mov	w2, #0x770              // =1904
    8494: 2a1f03e1     	mov	w1, wzr
    8498: 9400029e     	bl	0x8f10 <memset@plt>
    849c: 910253e8     	add	x8, sp, #0x94
    84a0: 91001100     	add	x0, x8, #0x4
    84a4: 52807702     	mov	w2, #0x3b8              // =952
    84a8: 2a1f03e1     	mov	w1, wzr
    84ac: 94000299     	bl	0x8f10 <memset@plt>
    84b0: f0000008     	adrp	x8, 0xb000
    84b4: d0ffffc4     	adrp	x4, 0x2000
    84b8: b0ffffc5     	adrp	x5, 0x1000
    84bc: 911c2108     	add	x8, x8, #0x708
    84c0: 52801f29     	mov	w9, #0xf9               // =249
    84c4: 6f00e400     	movi	v0.2d, #0000000000000000
    84c8: 91191484     	add	x4, x4, #0x645
    84cc: 9133b0a5     	add	x5, x5, #0xcec
    84d0: 910043e2     	add	x2, sp, #0x10
    84d4: 52800b40     	mov	w0, #0x5a               // =90
    84d8: 52800061     	mov	w1, #0x3                // =3
    84dc: 52801003     	mov	w3, #0x80               // =128
    84e0: 528008a6     	mov	w6, #0x45               // =69
    84e4: f9000288     	str	x8, [x20]
    84e8: b90097e9     	str	w9, [sp, #0x94]
    84ec: ad0383e0     	stp	q0, q0, [sp, #0x70]
    84f0: ad0283e0     	stp	q0, q0, [sp, #0x50]
    84f4: ad0183e0     	stp	q0, q0, [sp, #0x30]
    84f8: ad0083e0     	stp	q0, q0, [sp, #0x10]
    84fc: 94000291     	bl	0x8f40 <duss_log_gen_head@plt>
    8500: 340000e0     	cbz	w0, 0x851c <.text+0x451c>
    8504: b0ffffc3     	adrp	x3, 0x1000
    8508: 9125d063     	add	x3, x3, #0x974
    850c: 910043e2     	add	x2, sp, #0x10
    8510: 52800b40     	mov	w0, #0x5a               // =90
    8514: 52800061     	mov	w1, #0x3                // =3
    8518: 9400028e     	bl	0x8f50 <duss_log_print@plt>
    851c: 94000351     	bl	0x9260 <duss_sketch_get_config_file_name@plt>
    8520: d0ffffc1     	adrp	x1, 0x2000
    8524: b0ffffc2     	adrp	x2, 0x1000
    8528: 910a9021     	add	x1, x1, #0x2a4
    852c: 913c1842     	add	x2, x2, #0xf06
    8530: 910033e3     	add	x3, sp, #0xc
    8534: 912f23e4     	add	x4, sp, #0xbc8
    8538: 9400035a     	bl	0x92a0 <duss_mb_create_route_table@plt>
    853c: 35000bc0     	cbnz	w0, 0x86b4 <.text+0x46b4>
    8540: d0ffffc4     	adrp	x4, 0x2000
    8544: b0ffffc5     	adrp	x5, 0x1000
    8548: 6f00e400     	movi	v0.2d, #0000000000000000
    854c: 91191484     	add	x4, x4, #0x645
    8550: 9133b0a5     	add	x5, x5, #0xcec
    8554: 910043e2     	add	x2, sp, #0x10
    8558: 52800b40     	mov	w0, #0x5a               // =90
    855c: 52800061     	mov	w1, #0x3                // =3
    8560: 52801003     	mov	w3, #0x80               // =128
    8564: 52800a06     	mov	w6, #0x50               // =80
    8568: ad0383e0     	stp	q0, q0, [sp, #0x70]
    856c: ad0283e0     	stp	q0, q0, [sp, #0x50]
    8570: ad0183e0     	stp	q0, q0, [sp, #0x30]
    8574: ad0083e0     	stp	q0, q0, [sp, #0x10]
    8578: 94000272     	bl	0x8f40 <duss_log_gen_head@plt>
    857c: 340000e0     	cbz	w0, 0x8598 <.text+0x4598>
    8580: d0ffffc3     	adrp	x3, 0x2000
    8584: 91282863     	add	x3, x3, #0xa0a
    8588: 910043e2     	add	x2, sp, #0x10
    858c: 52800b40     	mov	w0, #0x5a               // =90
    8590: 52800061     	mov	w1, #0x3                // =3
    8594: 9400026f     	bl	0x8f50 <duss_log_print@plt>
    8598: 79401be1     	ldrh	w1, [sp, #0xc]
    859c: 91002274     	add	x20, x19, #0x8
    85a0: 912f23e0     	add	x0, sp, #0xbc8
    85a4: 911143e2     	add	x2, sp, #0x450
    85a8: 910253e3     	add	x3, sp, #0x94
    85ac: aa1303e4     	mov	x4, x19
    85b0: aa1403e5     	mov	x5, x20
    85b4: 9400033f     	bl	0x92b0 <duss_event_create_client@plt>
    85b8: 35000ae0     	cbnz	w0, 0x8714 <.text+0x4714>
    85bc: f9400280     	ldr	x0, [x20]
    85c0: 94000340     	bl	0x92c0 <duss_event_start_client@plt>
    85c4: 35000d00     	cbnz	w0, 0x8764 <.text+0x4764>
    85c8: 912f23e0     	add	x0, sp, #0xbc8
    85cc: 94000341     	bl	0x92d0 <duss_mb_destroy_route_table@plt>
    85d0: d0ffffc4     	adrp	x4, 0x2000
    85d4: b0ffffc5     	adrp	x5, 0x1000
    85d8: 6f00e400     	movi	v0.2d, #0000000000000000
    85dc: 91191484     	add	x4, x4, #0x645
    85e0: 9133b0a5     	add	x5, x5, #0xcec
    85e4: 910043e2     	add	x2, sp, #0x10
    85e8: 52800b40     	mov	w0, #0x5a               // =90
    85ec: 52800061     	mov	w1, #0x3                // =3
    85f0: 52801003     	mov	w3, #0x80               // =128
    85f4: 52800cc6     	mov	w6, #0x66               // =102
    85f8: ad0383e0     	stp	q0, q0, [sp, #0x70]
    85fc: ad0283e0     	stp	q0, q0, [sp, #0x50]
    8600: ad0183e0     	stp	q0, q0, [sp, #0x30]
    8604: ad0083e0     	stp	q0, q0, [sp, #0x10]
    8608: 9400024e     	bl	0x8f40 <duss_log_gen_head@plt>
    860c: 340000e0     	cbz	w0, 0x8628 <.text+0x4628>
    8610: b0ffffc3     	adrp	x3, 0x1000
    8614: 91358c63     	add	x3, x3, #0xd63
    8618: 910043e2     	add	x2, sp, #0x10
    861c: 52800b40     	mov	w0, #0x5a               // =90
    8620: 52800061     	mov	w1, #0x3                // =3
    8624: 9400024b     	bl	0x8f50 <duss_log_print@plt>
    8628: d0ffffc4     	adrp	x4, 0x2000
    862c: b0ffffc5     	adrp	x5, 0x1000
    8630: 6f00e400     	movi	v0.2d, #0000000000000000
    8634: 91191484     	add	x4, x4, #0x645
    8638: 9133b0a5     	add	x5, x5, #0xcec
    863c: 910043e2     	add	x2, sp, #0x10
    8640: 52800b40     	mov	w0, #0x5a               // =90
    8644: 52800061     	mov	w1, #0x3                // =3
    8648: 52801003     	mov	w3, #0x80               // =128
    864c: 52800d46     	mov	w6, #0x6a               // =106
    8650: a9015274     	stp	x20, x20, [x19, #0x10]
    8654: ad0383e0     	stp	q0, q0, [sp, #0x70]
    8658: ad0283e0     	stp	q0, q0, [sp, #0x50]
    865c: ad0183e0     	stp	q0, q0, [sp, #0x30]
    8660: ad0083e0     	stp	q0, q0, [sp, #0x10]
    8664: 94000237     	bl	0x8f40 <duss_log_gen_head@plt>
    8668: 34000100     	cbz	w0, 0x8688 <.text+0x4688>
    866c: f9400e64     	ldr	x4, [x19, #0x18]
    8670: d0ffffc3     	adrp	x3, 0x2000
    8674: 912b8c63     	add	x3, x3, #0xae3
    8678: 910043e2     	add	x2, sp, #0x10
    867c: 52800b40     	mov	w0, #0x5a               // =90
    8680: 52800061     	mov	w1, #0x3                // =3
    8684: 94000233     	bl	0x8f50 <duss_log_print@plt>
    8688: f94016a8     	ldr	x8, [x21, #0x28]
    868c: f85f83a9     	ldur	x9, [x29, #-0x8]
    8690: eb09011f     	cmp	x8, x9
    8694: 540009c1     	b.ne	0x87cc <.text+0x47cc>
    8698: 2a1f03e0     	mov	w0, wzr
    869c: 914007ff     	add	sp, sp, #0x1, lsl #12   // =0x1000
    86a0: 910f83ff     	add	sp, sp, #0x3e0
    86a4: a9424ff4     	ldp	x20, x19, [sp, #0x20]
    86a8: a94157fc     	ldp	x28, x21, [sp, #0x10]
    86ac: a8c37bfd     	ldp	x29, x30, [sp], #0x30
    86b0: d65f03c0     	ret
    86b4: d0ffffc4     	adrp	x4, 0x2000
    86b8: b0ffffc5     	adrp	x5, 0x1000
    86bc: 2a0003f4     	mov	w20, w0
    86c0: 6f00e400     	movi	v0.2d, #0000000000000000
    86c4: 91191484     	add	x4, x4, #0x645
    86c8: 9133b0a5     	add	x5, x5, #0xcec
    86cc: 910043e2     	add	x2, sp, #0x10
    86d0: 52800b40     	mov	w0, #0x5a               // =90
    86d4: 52800021     	mov	w1, #0x1                // =1
    86d8: 52801003     	mov	w3, #0x80               // =128
    86dc: 52800986     	mov	w6, #0x4c               // =76
    86e0: ad0383e0     	stp	q0, q0, [sp, #0x70]
    86e4: ad0283e0     	stp	q0, q0, [sp, #0x50]
    86e8: ad0183e0     	stp	q0, q0, [sp, #0x30]
    86ec: ad0083e0     	stp	q0, q0, [sp, #0x10]
    86f0: 94000214     	bl	0x8f40 <duss_log_gen_head@plt>
    86f4: 34000680     	cbz	w0, 0x87c4 <.text+0x47c4>
    86f8: d0ffffc3     	adrp	x3, 0x2000
    86fc: 910f0063     	add	x3, x3, #0x3c0
    8700: 910043e2     	add	x2, sp, #0x10
    8704: 52800b40     	mov	w0, #0x5a               // =90
    8708: 52800021     	mov	w1, #0x1                // =1
    870c: 2a1403e4     	mov	w4, w20
    8710: 1400002c     	b	0x87c0 <.text+0x47c0>
    8714: d0ffffc4     	adrp	x4, 0x2000
    8718: b0ffffc5     	adrp	x5, 0x1000
    871c: 2a0003f3     	mov	w19, w0
    8720: 6f00e400     	movi	v0.2d, #0000000000000000
    8724: 91191484     	add	x4, x4, #0x645
    8728: 9133b0a5     	add	x5, x5, #0xcec
    872c: 910043e2     	add	x2, sp, #0x10
    8730: 52800b40     	mov	w0, #0x5a               // =90
    8734: 52800021     	mov	w1, #0x1                // =1
    8738: 52801003     	mov	w3, #0x80               // =128
    873c: 52800b46     	mov	w6, #0x5a               // =90
    8740: ad0383e0     	stp	q0, q0, [sp, #0x70]
    8744: ad0283e0     	stp	q0, q0, [sp, #0x50]
    8748: ad0183e0     	stp	q0, q0, [sp, #0x30]
    874c: ad0083e0     	stp	q0, q0, [sp, #0x10]
    8750: 940001fc     	bl	0x8f40 <duss_log_gen_head@plt>
    8754: 34000380     	cbz	w0, 0x87c4 <.text+0x47c4>
    8758: d0ffffc3     	adrp	x3, 0x2000
    875c: 91015c63     	add	x3, x3, #0x57
    8760: 14000014     	b	0x87b0 <.text+0x47b0>
    8764: d0ffffc4     	adrp	x4, 0x2000
    8768: b0ffffc5     	adrp	x5, 0x1000
    876c: 2a0003f3     	mov	w19, w0
    8770: 6f00e400     	movi	v0.2d, #0000000000000000
    8774: 91191484     	add	x4, x4, #0x645
    8778: 9133b0a5     	add	x5, x5, #0xcec
    877c: 910043e2     	add	x2, sp, #0x10
    8780: 52800b40     	mov	w0, #0x5a               // =90
    8784: 52800021     	mov	w1, #0x1                // =1
    8788: 52801003     	mov	w3, #0x80               // =128
    878c: 52800c06     	mov	w6, #0x60               // =96
    8790: ad0383e0     	stp	q0, q0, [sp, #0x70]
    8794: ad0283e0     	stp	q0, q0, [sp, #0x50]
    8798: ad0183e0     	stp	q0, q0, [sp, #0x30]
    879c: ad0083e0     	stp	q0, q0, [sp, #0x10]
    87a0: 940001e8     	bl	0x8f40 <duss_log_gen_head@plt>
    87a4: 34000100     	cbz	w0, 0x87c4 <.text+0x47c4>
    87a8: b0ffffc3     	adrp	x3, 0x1000
    87ac: 91302463     	add	x3, x3, #0xc09
    87b0: 910043e2     	add	x2, sp, #0x10
    87b4: 52800b40     	mov	w0, #0x5a               // =90
    87b8: 52800021     	mov	w1, #0x1                // =1
    87bc: 2a1303e4     	mov	w4, w19
    87c0: 940001e4     	bl	0x8f50 <duss_log_print@plt>
    87c4: 12800000     	mov	w0, #-0x1               // =-1
    87c8: 940002b2     	bl	0x9290 <exit@plt>
    87cc: 9400020d     	bl	0x9000 <__stack_chk_fail@plt>
