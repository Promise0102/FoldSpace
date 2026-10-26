package com.mkf.foldspace;

/** Places a hosted task behind existing tasks; never deletes an intermediate root with children. */
final class BackgroundReturn {
    interface Bridge {
        Object createRoot()throws Exception;
        void lowerRoot(Object root)throws Exception;
        void moveIntoRoot(Object root)throws Exception;
        void detachAtBottom()throws Exception;
        boolean commitAndConfirm()throws Exception;
        boolean empty(Object root)throws Exception;
        void delete(Object root)throws Exception;
    }
    static boolean restore(Bridge bridge)throws Exception{
        Object root=null;
        try{root=bridge.createRoot();if(root==null)throw new IllegalStateException("No background return root");bridge.lowerRoot(root);bridge.moveIntoRoot(root);bridge.detachAtBottom();return bridge.commitAndConfirm();}
        finally{if(root!=null && bridge.empty(root))bridge.delete(root);}
    }
    private BackgroundReturn(){}
}
