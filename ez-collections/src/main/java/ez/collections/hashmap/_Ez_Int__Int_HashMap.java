package ez.collections.hashmap;

import ez.collections._Ez_Int__Int_Map;
import ez.collections._Ez_Int__Int_MapIterator;
import ez.collections.misc.PrimitiveHashCalculator;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

public class _Ez_Int__Int_HashMap implements _Ez_Int__Int_Map {
    private static final int DEFAULT_CAPACITY = 8;

    // There are three invariants for size, removedCount and arraysLength:
    // 1. size + removedCount <= 1/2 arraysLength
    // 2. size > 1/8 arraysLength
    // 3. size >= removedCount
    // arraysLength can be only multiplied by 2 and divided by 2.
    // Also, if it becomes >= 32, it can't become less anymore.
    private static final int REBUILD_LENGTH_THRESHOLD = 32;

    private static final int HASHCODE_INITIAL_VALUE = 0x811c9dc5;
    private static final int HASHCODE_MULTIPLIER = 0x01000193;

    private static final byte FREE = 0;
    private static final byte REMOVED = 1;
    private static final byte FILLED = 2;

    private static final /*V*/int/*V*/ DEFAULT_NULL_VALUE = (new /*V*/int/*V*/[1])[0];

    private static final Random rnd = new Random();
    private static final int POS_RANDOM_SHIFT_1;
    private static final int POS_RANDOM_SHIFT_2;
    private static final int STEP_RANDOM_SHIFT_1;
    private static final int STEP_RANDOM_SHIFT_2;

    static {
        POS_RANDOM_SHIFT_1 = rnd.nextInt(10) + 11;
        POS_RANDOM_SHIFT_2 = rnd.nextInt(10) + 21;
        STEP_RANDOM_SHIFT_1 = rnd.nextInt(10) + 11;
        STEP_RANDOM_SHIFT_2 = rnd.nextInt(10) + 21;
    }

    private /*K*/int/*K*/[] keys;
    private /*V*/int/*V*/[] values;
    private byte[] status;
    private int size;
    private int removedCount;
    private int mask;
    private boolean returnedNull;
    private final int hashSeed;

    public _Ez_Int__Int_HashMap() {
        this(DEFAULT_CAPACITY);
    }

    public _Ez_Int__Int_HashMap(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must be non-negative");
        }
        // Actually we need 4x more memory
        int length = 4 * Math.max(1, capacity);
        if ((length & (length - 1)) != 0) {
            length = Integer.highestOneBit(length) << 1;
        }
        // Length is a power of 2 now
        initEmptyTable(length);
        returnedNull = false;
        hashSeed = rnd.nextInt();
    }

    public _Ez_Int__Int_HashMap(_Ez_Int__Int_Map map) {
        this(map.size());
        for (_Ez_Int__Int_MapIterator it = map.iterator(); it.hasNext(); it.next()) {
            put(it.getKey(), it.getValue());
        }
    }

    public _Ez_Int__Int_HashMap(Map</*KW*/Integer/*KW*/, /*VW*/Integer/*VW*/> javaMap) {
        this(javaMap.size());
        for (Map.Entry</*KW*/Integer/*KW*/, /*VW*/Integer/*VW*/> e : javaMap.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    private int getStartPos(int h) {
        h ^= hashSeed;
        h ^= (h >>> POS_RANDOM_SHIFT_1) ^ (h >>> POS_RANDOM_SHIFT_2);
        return h & mask;
    }

    private int getStep(int h) {
        h ^= hashSeed;
        h ^= (h >>> STEP_RANDOM_SHIFT_1) ^ (h >>> STEP_RANDOM_SHIFT_2);
        return ((h << 1) | 1) & mask;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsKey(/*K*/int/*K*/ key) {
        final int keyHash = PrimitiveHashCalculator.getHash(key);
        int pos = getStartPos(keyHash);
        final int step = getStep(keyHash);
        for (; status[pos] != FREE; pos = (pos + step) & mask) {
            if (status[pos] == FILLED && keys[pos] == key) {
                return true;
            }
        }
        return false;
    }

    @Override
    public /*V*/int/*V*/ get(/*K*/int/*K*/ key) {
        final int keyHash = PrimitiveHashCalculator.getHash(key);
        int pos = getStartPos(keyHash);
        final int step = getStep(keyHash);
        for (; status[pos] != FREE; pos = (pos + step) & mask) {
            if (status[pos] == FILLED && keys[pos] == key) {
                returnedNull = false;
                return values[pos];
            }
        }
        returnedNull = true;
        return DEFAULT_NULL_VALUE;
    }

    @Override
    public /*V*/int/*V*/ put(/*K*/int/*K*/ key, /*V*/int/*V*/ value) {
        final int keyHash = PrimitiveHashCalculator.getHash(key);
        int pos = getStartPos(keyHash);
        final int step = getStep(keyHash);
        for (; status[pos] == FILLED; pos = (pos + step) & mask) {
            if (keys[pos] == key) {
                final /*V*/int/*V*/ oldValue = values[pos];
                values[pos] = value;
                returnedNull = false;
                return oldValue;
            }
        }
        if (status[pos] == FREE) {
            status[pos] = FILLED;
            keys[pos] = key;
            values[pos] = value;
            size++;
            if ((size + removedCount) * 2 > keys.length) {
                rebuild(keys.length * 2); // enlarge the table
            }
            returnedNull = true;
            return DEFAULT_NULL_VALUE;
        }
        final int removedPos = pos;
        for (pos = (pos + step) & mask; status[pos] != FREE; pos = (pos + step) & mask) {
            if (status[pos] == FILLED && keys[pos] == key) {
                final /*V*/int/*V*/ oldValue = values[pos];
                values[pos] = value;
                returnedNull = false;
                return oldValue;
            }
        }
        status[removedPos] = FILLED;
        keys[removedPos] = key;
        values[removedPos] = value;
        size++;
        removedCount--;
        returnedNull = true;
        return DEFAULT_NULL_VALUE;
    }

    @Override
    public /*V*/int/*V*/ remove(/*K*/int/*K*/ key) {
        final int keyHash = PrimitiveHashCalculator.getHash(key);
        int pos = getStartPos(keyHash);
        final int step = getStep(keyHash);
        for (; status[pos] != FREE; pos = (pos + step) & mask) {
            if (status[pos] == FILLED && keys[pos] == key) {
                final /*V*/int/*V*/ removedValue = values[pos];
                status[pos] = REMOVED;
                size--;
                removedCount++;
                if (keys.length > REBUILD_LENGTH_THRESHOLD) {
                    if (8 * size <= keys.length) {
                        rebuild(keys.length / 2); // compress the table
                    } else if (size < removedCount) {
                        rebuild(keys.length); // just rebuild the table
                    }
                }
                returnedNull = false;
                return removedValue;
            }
        }
        returnedNull = true;
        return DEFAULT_NULL_VALUE;
    }

    @Override
    public boolean returnedNull() {
        return returnedNull;
    }

    @Override
    public void clear() {
        if (keys.length > REBUILD_LENGTH_THRESHOLD) {
            initEmptyTable(REBUILD_LENGTH_THRESHOLD);
        } else {
            Arrays.fill(status, FREE);
            size = 0;
            removedCount = 0;
        }
    }

    @Override
    public /*K*/int/*K*/[] keys() {
        /*K*/int/*K*/[] result = new /*K*/int/*K*/[size];
        for (int i = 0, j = 0; i < keys.length; i++) {
            if (status[i] == FILLED) {
                result[j++] = keys[i];
            }
        }
        return result;
    }

    @Override
    public /*V*/int/*V*/[] values() {
        /*V*/int/*V*/[] result = new /*V*/int/*V*/[size];
        for (int i = 0, j = 0; i < values.length; i++) {
            if (status[i] == FILLED) {
                result[j++] = values[i];
            }
        }
        return result;
    }

    @Override
    public _Ez_Int__Int_MapIterator iterator() {
        return new _Ez_Int__Int_HashMapIterator();
    }

    private void rebuild(int newLength) {
        /*K*/int/*K*/[] oldKeys = keys;
        /*V*/int/*V*/[] oldValues = values;
        byte[] oldStatus = status;
        initEmptyTable(newLength);
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldStatus[i] == FILLED) {
                put(oldKeys[i], oldValues[i]);
            }
        }
    }

    private void initEmptyTable(int length) {
        keys = new /*K*/int/*K*/[length];
        values = new /*V*/int/*V*/[length];
        status = new byte[length];
        size = 0;
        removedCount = 0;
        mask = length - 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        _Ez_Int__Int_HashMap that = (_Ez_Int__Int_HashMap) o;

        if (size != that.size) {
            return false;
        }
        for (int i = 0; i < keys.length; i++) {
            if (status[i] == FILLED) {
                /*V*/int/*V*/ thatValue = that.get(keys[i]);
                if (that.returnedNull || thatValue != values[i]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void randomShuffle(int[] array) {
        int n = array.length;
        for (int i = 0; i < n; i++) {
            int j = i + rnd.nextInt(n - i);
            int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    @Override
    public int hashCode() {
        int[] entryHashes = new int[size];
        for (int i = 0, j = 0; i < status.length; i++) {
            if (status[i] == FILLED) {
                int hash = HASHCODE_INITIAL_VALUE;
                hash = (hash ^ PrimitiveHashCalculator.getHash(keys[i])) * HASHCODE_MULTIPLIER;
                hash = (hash ^ PrimitiveHashCalculator.getHash(values[i])) * HASHCODE_MULTIPLIER;
                entryHashes[j++] = hash;
            }
        }
        randomShuffle(entryHashes);
        Arrays.sort(entryHashes);
        int hash = HASHCODE_INITIAL_VALUE;
        for (int i = 0; i < size; i++) {
            hash = (hash ^ entryHashes[i]) * HASHCODE_MULTIPLIER;
        }
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (int i = 0; i < keys.length; i++) {
            if (status[i] == FILLED) {
                if (sb.length() > 1) {
                    sb.append(", ");
                }
                sb.append(keys[i]);
                sb.append('=');
                sb.append(values[i]);
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private class _Ez_Int__Int_HashMapIterator implements _Ez_Int__Int_MapIterator {
        private int curIndex;

        private _Ez_Int__Int_HashMapIterator() {
            curIndex = 0;
            while (curIndex < status.length && status[curIndex] != FILLED) {
                curIndex++;
            }
        }

        @Override
        public boolean hasNext() {
            return curIndex < status.length;
        }

        @Override
        public /*K*/int/*K*/ getKey() {
            if (curIndex == keys.length) {
                throw new NoSuchElementException("Iterator doesn't have more entries");
            }
            return keys[curIndex];
        }

        @Override
        public /*V*/int/*V*/ getValue() {
            if (curIndex == values.length) {
                throw new NoSuchElementException("Iterator doesn't have more entries");
            }
            return values[curIndex];
        }

        @Override
        public void next() {
            if (curIndex == status.length) {
                return;
            }
            curIndex++;
            while (curIndex < status.length && status[curIndex] != FILLED) {
                curIndex++;
            }
        }
    }
}
