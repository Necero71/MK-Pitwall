package com.consoletelemetry.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** All three games send little-endian packets. */
internal fun ByteArray.le(length: Int = size): ByteBuffer =
    ByteBuffer.wrap(this, 0, length).order(ByteOrder.LITTLE_ENDIAN)

internal fun ByteBuffer.u8(offset: Int): Int = get(offset).toInt() and 0xFF
internal fun ByteBuffer.s8(offset: Int): Int = get(offset).toInt()
internal fun ByteBuffer.u16(offset: Int): Int = getShort(offset).toInt() and 0xFFFF
internal fun ByteBuffer.s16(offset: Int): Int = getShort(offset).toInt()
internal fun ByteBuffer.u32(offset: Int): Long = getInt(offset).toLong() and 0xFFFFFFFFL
internal fun ByteBuffer.f32(offset: Int): Float = getFloat(offset)
