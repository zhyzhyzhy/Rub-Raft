package cc.lovezhy.raft.rpc.util;

import io.netty.util.Recycler;

public class LockObjectFactory {

    private static final Recycler<LockObject> LOCK_OBJECT_RECYCLER = new Recycler<LockObject>() {
        @Override
        protected LockObject newObject(Handle<LockObject> handle) {
            return new LockObject(handle);
        }
    };

    public static LockObject getLockObject() {
        return LOCK_OBJECT_RECYCLER.get();
    }

    public static class LockObject {
        private Recycler.Handle<LockObject> handle;

        private LockObject(Recycler.Handle<LockObject> handle) {
            this.handle = handle;
        }

        public void recycle() {
            this.handle.recycle(this);
        }
    }
}
