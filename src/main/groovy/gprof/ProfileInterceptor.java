package gprof;

import groovy.lang.Interceptor;

import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ProfileInterceptor implements Interceptor {

    private ConcurrentMap<Thread, LocalInterceptor> interceptors;
    private ProfileCallTree tree;

    public ProfileInterceptor() {
        interceptors = new ConcurrentHashMap<Thread, LocalInterceptor>();
    }

    protected boolean isCallToBeExcluded(Object object, String methodName) {
        return isCallToBeExcluded(classNameOf(object), methodName);
    }

    protected boolean isCallToBeExcluded(String className, String methodName) {
        return className.equals("groovy.grape.Grape") && methodName.equals("grab");
    }

    protected String classNameOf(Object object) {
        String className;
        if (object.getClass() == Class.class /* static methods */) {
            className = ((Class) object).getName();
        } else {
            className = object.getClass().getName();
        }
        return className;
    }

    private LocalInterceptor getLocalInterceptor() {
        LocalInterceptor theInterceptor = interceptors.get(Thread.currentThread());
        if (theInterceptor == null) {
            theInterceptor = new LocalInterceptor();
            interceptors.put(Thread.currentThread(), theInterceptor);
        }
        return theInterceptor;
    }

    @Override
    public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
        return getLocalInterceptor().beforeInvoke(object, methodName, arguments);
    }

    @Override
    public Object afterInvoke(Object object, String methodName, Object[] arguments, Object result) {
        return getLocalInterceptor().afterInvoke(object, methodName, arguments, result);
    }

    @Override
    public boolean doInvoke() {
        return getLocalInterceptor().doInvoke();
    }

    public ProfileCallTree getTree() {
        if (tree == null) {
            tree = makeTree();
        }
        return tree;
    }

    ProfileCallTree makeTree() {
        // Wait for all the child threads to die.
        Thread profThread = Thread.currentThread();
        for (Thread thread : interceptors.keySet()) {
            if (!thread.equals(profThread)) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        ProfileCallTree tree = new ProfileCallTree();
        for (LocalInterceptor interceptor : interceptors.values()) {
            ProfileCallTree theTree = interceptor.getTree();
            tree.getRoot().getChildren().addAll(theTree.getRoot().getChildren());
        }
        return tree;
    }

    static class LocalInterceptor implements Interceptor {

        private ProfileCallTree tree;
        private ProfileCallTree tmpTree;
        private Stack<ProfileCallTree.Node> nodeStack;
        private Stack<Long> timeStack;
        private boolean ignoring;

        public LocalInterceptor() {
            tmpTree = new ProfileCallTree();
            nodeStack = new Stack();
            nodeStack.push(tmpTree.getRoot());
            timeStack = new Stack();
        }

        private boolean isCallToBeExcluded(Object object, String methodName) {
            return isCallToBeExcluded(classNameOf(object), methodName);
        }

        private boolean isCallToBeExcluded(String className, String methodName) {
            return className.equals("groovy.grape.Grape") && methodName.equals("grab");
        }

        private String classNameOf(Object object) {
            String className;
            if (object.getClass() == Class.class /* static methods */) {
                className = ((Class) object).getName();
            } else {
                className = object.getClass().getName();
            }
            return className;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if (ignoring) {
                // Skip while processing a call to be excluded.
                return null;
            }
            ignoring = isCallToBeExcluded(object, methodName);

            ProfileCallTree.Node node = new ProfileCallTree.Node(new ProfileCallEntry(classNameOf(object), methodName));
            ProfileCallTree.Node parentNode = nodeStack.peek();
            parentNode.addChild(node);
            node.setParent(parentNode);

            nodeStack.push(node);
            timeStack.push(System.nanoTime());
            return null;
        }

        @Override
        public Object afterInvoke(Object object, String methodName, Object[] arguments, Object result) {
            if (ignoring && (ignoring = !isCallToBeExcluded(object, methodName))) {
                // Skip while processing a call to be excluded.
                return result;
            }
            long time = System.nanoTime() - timeStack.pop();
            ProfileCallTree.Node node = nodeStack.pop();
            node.getData().setTime(new ProfileTime(time));
            return result;
        }

        @Override
        public boolean doInvoke() {
            return true;
        }

        public ProfileCallTree getTree() {
            if (tree == null) {
                tree = makeTree();
            }
            return tree;
        }

        ProfileCallTree makeTree() {
            ProfileCallTree tree = tmpTree;
            tree.visit(new ProfileCallTree.NodeVisitor() {
                @Override
                public void visit(ProfileCallTree.Node node) {
                    long time = node.getData().getTime().nanoseconds();
                    if (node.hasChildren()) {
                        for (ProfileCallTree.Node child : node.getChildren()) {
                            ProfileCallEntry callEntry = child.getData();
                            time -= callEntry.getTime().nanoseconds();
                        }
                    }
                    node.getData().setTime(new ProfileTime(time));
                    List<ProfileCallTree.Node> childNodes = node.getChildren();
                    for (int i = childNodes.size() - 1; i >= 0; i--) {
                        ProfileCallTree.Node child = childNodes.get(i);
                        ProfileCallEntry childCall = child.getData();
                        if (isCallToBeExcluded(childCall.getClassName(), childCall.getMethodName())) {
                            childNodes.remove(i);
                        }
                    }
                }
            });
            return tree;
        }

    }

}