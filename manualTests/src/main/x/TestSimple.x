module TestSimple {
    void run() {

    Boolean flag1 = True;
    Boolean flag2 = False;
    assert:debug flag1, flag2 as "this should be ignored but used to NPE";
    }
}