package com.hustunique.apng_decoder

import java.nio.ByteBuffer

/**
 * Copyright (C) 2021 little-csd
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

class APngObject(data: ByteBuffer) {
    private lateinit var header: IHDRChunk
    private var actl: ACTLChunk? = null
    private val frames = ArrayList<RawFrameData>()
    private val others = ArrayList<BaseChunk>()
    private var rawFrame: RawFrameData? = null

    companion object {
        const val PNG_SIGNATURE = (0x89_50_4E_47 shl 32) + 0x0D_0A_1A_0A
    }

    init {
        readAndCheckSignature(data)
        while (data.remaining() > 0) {
            val chunk = readAndUnBox(data)
            process(chunk)
        }
        check(this::header.isInitialized) {
            "Png file has no IHDRChunk"
        }
        rawFrame?.apply { frames.add(this) }
    }

    fun getFrame(index: Int): RawFrameData = frames[index]

    fun getFrames(): List<RawFrameData> = frames

    fun getOthersChunk(): List<BaseChunk> = others

    fun getHeader(): IHDRChunk = header

    private fun readAndCheckSignature(data: ByteBuffer) {
        val signature = data.long
        check(signature == PNG_SIGNATURE) { "Not a PNG file!!!" }
    }

    private fun readAndUnBox(box: ByteBuffer): BaseChunk {
        val pos = box.position()
        val chunkLen = box.int + 12
        val type = box.int
        // must add slice call
        val chunk =
            BaseChunk.makeChunk(
                type,
                ByteBuffer.wrap(box.array(), pos, chunkLen).slice()
            )
        box.position(pos + chunkLen)
        return chunk
    }

    private fun process(chunk: BaseChunk) {
        when (chunk) {
            is IHDRChunk -> {
                header = chunk
            }
            is ACTLChunk -> {
                check(actl == null) { "Two acTL Chunk" }
                actl = chunk
            }
            is FCTLChunk -> {
                check(this::header.isInitialized) { "Header not initialized" }
                rawFrame?.apply { frames.add(this) }
                rawFrame = RawFrameData(chunk, ArrayList())
            }
            is FDATChunk -> {
                check(rawFrame != null) { "fdAT Chunk before fcTL chunk" }
                rawFrame?.chunks?.add(chunk)
            }
            is IDATChunk -> rawFrame?.chunks?.add(chunk)
            else -> others.add(chunk)
        }
    }
}