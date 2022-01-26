/**
 * AnyStream
 * Copyright (C) 2021 AnyStream Maintainers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package anystream.util

import java.io.Serializable
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * A globally unique identifier for objects.
 */
class ObjectId : Comparable<ObjectId?>, Serializable {
    /**
     * Gets the timestamp (number of seconds since the Unix epoch).
     *
     * @return the timestamp
     */
    private val timestamp: Int
    private val counter: Int
    private val randomValue1: Int
    private val randomValue2: Short

    /**
     * Create a new object id.
     */
    constructor(date: Date = Date()) : this(
        dateToTimestampSeconds(date),
        NEXT_COUNTER.getAndIncrement() and LOW_ORDER_THREE_BYTES,
        false
    )

    /**
     * Constructs a new instances using the given date and counter.
     *
     * @param date    the date
     * @param counter the counter
     * @throws IllegalArgumentException if the high order byte of counter is not zero
     */
    constructor(date: Date, counter: Int) : this(dateToTimestampSeconds(date), counter, true) {}

    /**
     * Creates an ObjectId using the given time, machine identifier, process identifier, and counter.
     *
     * @param timestamp the time in seconds
     * @param counter   the counter
     * @throws IllegalArgumentException if the high order byte of counter is not zero
     */
    constructor(timestamp: Int, counter: Int) : this(timestamp, counter, true) {}
    private constructor(timestamp: Int, counter: Int, checkCounter: Boolean) : this(
        timestamp,
        RANDOM_VALUE1,
        RANDOM_VALUE2,
        counter,
        checkCounter
    ) {
    }

    private constructor(
        timestamp: Int,
        randomValue1: Int,
        randomValue2: Short,
        counter: Int,
        checkCounter: Boolean
    ) {
        require(randomValue1 and -0x1000000 == 0) { "The machine identifier must be between 0 and 16777215 (it must fit in three bytes)." }
        require(!(checkCounter && counter and -0x1000000 != 0)) { "The counter must be between 0 and 16777215 (it must fit in three bytes)." }
        this.timestamp = timestamp
        this.counter = counter and LOW_ORDER_THREE_BYTES
        this.randomValue1 = randomValue1
        this.randomValue2 = randomValue2
    }

    /**
     * Constructs a new instance from a 24-byte hexadecimal string representation.
     *
     * @param hexString the string to convert
     * @throws IllegalArgumentException if the string is not a valid hex string representation of an ObjectId
     */
    constructor(hexString: String) : this(parseHexString(hexString)) {}

    /**
     * Constructs a new instance from the given byte array
     *
     * @param bytes the byte array
     * @throws IllegalArgumentException if array is not of length 12
     */
    constructor(bytes: ByteArray) : this(ByteBuffer.wrap(bytes)) {
        check(bytes.size == 12) { "buffer.remaining() == 12" }
    }

    /**
     * Constructs a new instance from the given ByteBuffer
     *
     * @param buffer the ByteBuffer
     * @throws IllegalArgumentException if the buffer is null or does not have at least 12 bytes remaining
     */
    constructor(buffer: ByteBuffer) {
        check(buffer.remaining() >= OBJECT_ID_LENGTH) { "buffer.remaining() >= 12" }

        // Note: Cannot use ByteBuffer.getInt because it depends on tbe buffer's byte order
        // and ObjectId's are always in big-endian order.
        timestamp = makeInt(buffer.get(), buffer.get(), buffer.get(), buffer.get())
        randomValue1 = makeInt(0.toByte(), buffer.get(), buffer.get(), buffer.get())
        randomValue2 = makeShort(buffer.get(), buffer.get())
        counter = makeInt(0.toByte(), buffer.get(), buffer.get(), buffer.get())
    }

    /**
     * Convert to a byte array.  Note that the numbers are stored in big-endian order.
     *
     * @return the byte array
     */
    fun toByteArray(): ByteArray {
        val buffer: ByteBuffer = ByteBuffer.allocate(OBJECT_ID_LENGTH)
        putToByteBuffer(buffer)
        return buffer.array() // using .allocate ensures there is a backing array that can be returned
    }

    /**
     * Convert to bytes and put those bytes to the provided ByteBuffer.
     * Note that the numbers are stored in big-endian order.
     *
     * @param buffer the ByteBuffer
     * @throws IllegalArgumentException if the buffer is null or does not have at least 12 bytes remaining
     */
    fun putToByteBuffer(buffer: ByteBuffer) {
        check(buffer.remaining() >= OBJECT_ID_LENGTH) { "buffer.remaining() >= 12" }
        buffer.put(int3(timestamp))
        buffer.put(int2(timestamp))
        buffer.put(int1(timestamp))
        buffer.put(int0(timestamp))
        buffer.put(int2(randomValue1))
        buffer.put(int1(randomValue1))
        buffer.put(int0(randomValue1))
        buffer.put(short1(randomValue2))
        buffer.put(short0(randomValue2))
        buffer.put(int2(counter))
        buffer.put(int1(counter))
        buffer.put(int0(counter))
    }

    /**
     * Gets the timestamp as a `Date` instance.
     *
     * @return the Date
     */
    val date: Date
        get() = Date((timestamp.toLong() and 0xFFFFFFFFL) * 1000L)

    /**
     * Converts this instance into a 24-byte hexadecimal string representation.
     *
     * @return a string representation of the ObjectId in hexadecimal format
     */
    fun toHexString(): String {
        val chars = CharArray(OBJECT_ID_LENGTH * 2)
        var i = 0
        for (b in toByteArray()) {
            chars[i++] = HEX_CHARS[b.toInt() shr 4 and 0xF]
            chars[i++] = HEX_CHARS[b.toInt() and 0xF]
        }
        return String(chars)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val objectId = other as ObjectId
        if (counter != objectId.counter) {
            return false
        }
        if (timestamp != objectId.timestamp) {
            return false
        }
        if (randomValue1 != objectId.randomValue1) {
            return false
        }
        return randomValue2 == objectId.randomValue2
    }

    override fun hashCode(): Int {
        var result = timestamp
        result = 31 * result + counter
        result = 31 * result + randomValue1
        result = 31 * result + randomValue2
        return result
    }

    override operator fun compareTo(other: ObjectId?): Int {
        checkNotNull(other)
        val byteArray = toByteArray()
        val otherByteArray = other.toByteArray()
        for (i in 0 until OBJECT_ID_LENGTH) {
            if (byteArray[i] != otherByteArray[i]) {
                return if (byteArray[i].toInt() and 0xff < otherByteArray[i].toInt() and 0xff) -1 else 1
            }
        }
        return 0
    }

    override fun toString(): String {
        return toHexString()
    }

    // see https://docs.oracle.com/javase/6/docs/platform/serialization/spec/output.html
    private fun writeReplace(): Any {
        return SerializationProxy(this)
    }

    private class SerializationProxy internal constructor(objectId: ObjectId) : Serializable {
        private val bytes: ByteArray

        init {
            bytes = objectId.toByteArray()
        }

        private fun readResolve(): Any {
            return ObjectId(bytes)
        }

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        // unused, as this class uses a proxy for serialization
        private const val serialVersionUID = 1L
        private const val OBJECT_ID_LENGTH = 12
        private const val LOW_ORDER_THREE_BYTES = 0x00ffffff

        // Use primitives to represent the 5-byte random value.
        private var RANDOM_VALUE1 = 0
        private var RANDOM_VALUE2: Short = 0
        private val NEXT_COUNTER: AtomicInteger = AtomicInteger(SecureRandom().nextInt())
        private val HEX_CHARS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        )

        /**
         * Gets a new object id.
         *
         * @return the new id
         */
        fun get(): ObjectId {
            return ObjectId()
        }

        /**
         * Gets a new object id with the given date value and all other bits zeroed.
         *
         *
         * The returned object id will compare as less than or equal to any other object id within the same second as the given date, and
         * less than any later date.
         *
         *
         * @param date the date
         * @return the ObjectId
         * @since 4.1
         */
        fun getSmallestWithDate(date: Date): ObjectId {
            return ObjectId(dateToTimestampSeconds(date), 0, 0.toShort(), 0, false)
        }

        /**
         * Checks if a string could be an `ObjectId`.
         *
         * @param hexString a potential ObjectId as a String.
         * @return whether the string could be an object id
         * @throws IllegalArgumentException if hexString is null
         */
        fun isValid(hexString: String?): Boolean {
            requireNotNull(hexString)
            val len = hexString.length
            if (len != 24) {
                return false
            }
            for (i in 0 until len) {
                when (hexString[i]) {
                    in '0'..'9' -> continue
                    in 'a'..'f' -> continue
                    in 'A'..'F' -> continue
                    else -> return false
                }
            }
            return true
        }

        init {
            try {
                val secureRandom = SecureRandom()
                RANDOM_VALUE1 = secureRandom.nextInt(0x01000000)
                RANDOM_VALUE2 = secureRandom.nextInt(0x00008000).toShort()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        private fun parseHexString(s: String): ByteArray {
            require(isValid(s)) { "invalid hexadecimal representation of an ObjectId: [$s]" }
            val b = ByteArray(OBJECT_ID_LENGTH)
            for (i in b.indices) {
                b[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return b
        }

        private fun dateToTimestampSeconds(time: Date): Int {
            return (time.time / 1000L).toInt()
        }

        // Big-Endian helpers, in this class because all other BSON numbers are little-endian
        private fun makeInt(b3: Byte, b2: Byte, b1: Byte, b0: Byte): Int {
            return b3.toInt() shl 24 or
                (b2.toInt() and 0xff shl 16) or
                (b1.toInt() and 0xff shl 8) or
                (b0.toInt() and 0xff)
        }

        private fun makeShort(b1: Byte, b0: Byte): Short {
            return (b1.toInt() and 0xff shl 8 or (b0.toInt() and 0xff)).toShort()
        }

        private fun int3(x: Int): Byte {
            return (x shr 24).toByte()
        }

        private fun int2(x: Int): Byte {
            return (x shr 16).toByte()
        }

        private fun int1(x: Int): Byte {
            return (x shr 8).toByte()
        }

        private fun int0(x: Int): Byte {
            return x.toByte()
        }

        private fun short1(x: Short): Byte {
            return (x.toInt() shr 8).toByte()
        }

        private fun short0(x: Short): Byte {
            return x.toByte()
        }
    }
}
