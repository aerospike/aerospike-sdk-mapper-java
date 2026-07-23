package com.aerospike.mapper.tools;

public class DeferredObjectLoader {
    public interface DeferredSetter {
        void setValue(Object object);
    }

    public static class DeferredObject {
        private final Object key;
        private final Class<?> type;
        private final boolean isDigest;

        public DeferredObject(Object key, Class<?> type, boolean isDigest) {
            super();
            this.key = key;
            this.type = type;
            this.isDigest = isDigest;
        }

        public Object getKey() {
            return key;
        }

        public Class<?> getType() {
            return type;
        }

        public boolean isDigest() {
            return isDigest;
        }
    }

    public static class DeferredObjectSetter {
        private final DeferredSetter setter;
        private final DeferredObject object;

        public DeferredObjectSetter(DeferredSetter setter, DeferredObject object) {
            super();
            this.setter = setter;
            this.object = object;
        }

        public DeferredSetter getSetter() {
            return setter;
        }

        public DeferredObject getObject() {
            return object;
        }
    }
}
