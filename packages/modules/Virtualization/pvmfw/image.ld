/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

MEMORY
{
	dtb_region	: ORIGIN = 0x80000000, LENGTH = 2M
	image		: ORIGIN = 0x80200000, LENGTH = 2M
	writable_data	: ORIGIN = 0x80400000, LENGTH = 2M
}

/*
 * Code will start running at this symbol which is placed at the start of the
 * image.
 */
ENTRY(entry)

/*
 * The following would be useful to check that .init code is not called back
 * into once it has completed but it isn't supported by ld.lld.
 *
 * NOCROSSREFS_TO(.init .text)
 */

SECTIONS
{
	.dtb (NOLOAD) : {
		dtb_begin = .;
		. += LENGTH(dtb_region);
		dtb_end = .;
	} >dtb_region

	/*
	 * Collect together the code. This is page aligned so it can be mapped
	 * as executable-only.
	 */
	.init : ALIGN(4096) {
		text_begin = .;
		*(.init.entry)
		*(.init.*)
	} >image
	.text : {
		*(.text.*)
	} >image
	text_end = .;

	/*
	 * Collect together read-only data. This is page aligned so it can be
	 * mapped as read-only and non-executable.
	 */
	.rodata : ALIGN(4096) {
		rodata_begin = .;
		*(.rodata.*)
	} >image
	.got : {
		*(.got)
	} >image
	rodata_end = .;

	/*
	 * Collect together the read-write data including .bss at the end which
	 * will be zero'd by the entry code. This is page aligned so it can be
	 * mapped as non-executable.
	 */
	.data : ALIGN(4096) {
		data_begin = .;
		*(.data.*)
		/*
		 * The entry point code assumes that .data is a multiple of 32
		 * bytes long.
		 */
		. = ALIGN(32);
		data_end = .;
	} >writable_data AT>image
	data_lma = LOADADDR(.data);

	/* Everything beyond this point will not be included in the binary. */
	bin_end = .;

	/* The entry point code assumes that .bss is 16-byte aligned. */
	.bss : ALIGN(16)  {
		bss_begin = .;
		*(.bss.*)
		*(COMMON)
		. = ALIGN(16);
		bss_end = .;
	} >writable_data

	.stack (NOLOAD) : ALIGN(4096) {
		boot_stack_begin = .;
		. += 40 * 4096;
		. = ALIGN(4096);
		boot_stack_end = .;
	} >writable_data

	/*
	 * Remove unused sections from the image.
	 */
	/DISCARD/ : {
		/* The image loads itself so doesn't need these sections. */
		*(.gnu.hash)
		*(.hash)
		*(.interp)
		*(.eh_frame_hdr)
		*(.eh_frame)
		*(.note.gnu.build-id)
	}
}
