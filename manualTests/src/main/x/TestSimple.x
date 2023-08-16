    module TestSimple {
        @Inject Console console;

        void run() {
            Callback[] callbacks = new Array();
            callbacks.add(new MyCallbackOne());
            callbacks.add(new MyCallbackTwo());

            // the line below used to throw TypeMismatch at run-time; it no longer compiles
            // Callback[] filtered = callbacks.filter(c -> !c.is(MyCallbackOne));
            Collection<Callback> filtered = callbacks.filter(c -> !c.is(MyCallbackOne));
            for (Callback callback : filtered) {
                callback.call();
            }

            for (Callback callback : callbacks.filter(c -> c.is(MyCallbackOne))) {
                callback.call();
            }
        }

        interface Callback {
            void call();
        }

        const MyCallbackOne
                implements Callback {
            @Override
            void call() {
                console.print("one");
            }
        }

        const MyCallbackTwo
                implements Callback {
            @Override
            void call() {
                console.print("two");
            }
        }
    }