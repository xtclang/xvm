module TestSimple
    {
    @Inject Console console;

    void run()
        {
        PI p = new Parent();
        CI c = p.child;

        p.onParent();
        c.onChild();
        }

    interface PI
        {
        void onParent();
        CI   child;
        }

    interface CI
        {
        void onChild();
        }

    service Parent
            implements PI
        {
        construct()
            {
            }
        finally
            {
            child = new Child();
            }

        @Override
        @Unassigned @Atomic Child child.get()
            {
            console.println($"child.get() {this:service}");
            return super();
            }

        @Override
        void onParent()
            {
            console.println($"onParent() {this:service}");
            }

        private void fromChild()
            {
            console.println($"fromChild() {this:service}");
            }

        class Child
                implements CI
            {
            @Override
            void onChild()
                {
                console.println($"onChild() {this:service}");
                fromChild();
                }
            }
        }
    }
