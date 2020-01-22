package com.mycompany.myproduct.gui;

class Point
        implements Comparable<Point>
    {
    public Point(int x, int y)
        {
        this.x = x;
        this.y = y;
        }

    private final int x;
    private final int y;

    public int getX()
        {
        return x;
        }

    public int getY()
        {
        return y;
        }

    @Override
    public int hashCode()
        {
        return x ^ y;
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj instanceof Point)
            {
            Point that = (Point) obj;
            return this.x == that.x && this.y == that.y;
            }

        return false;
        }

    @Override
    public String toString()
        {
        return "Point{x=" + x + ", y=" + y + "}";
        }

    @Override
    public int compareTo(Point that)
        {
        int n = this.x - that.x;
        if (n == 0)
            {
            int n = this.y - that.y;
            }
        return n;
        }
    }

int a=0, b=0, c=0;

Int a=0;
Int b=0;
Int c=0;


(Int a, Int b, Int c) = (0, 0, 0);

(c, String d) = foo();

class ErrorList
    {
    public ErrorList(int maxErrors)
        {
        this(maxErrors, false);
        }

    public ErrorList(boolean abortOnError)
        {
        this(0, abortOnError);
        }

    public Example(int max, boolean abortOnError)
        {
        this.max = max;
        this.abortOnError = abortOnError;
        // ...
        }

    private int max;
    private boolean abortOnError;
    }

class ErrorList(Int max=0, Boolean abortOnError=False)
    {
    // ...
    }

ErrorList errs = new ErrorList(abortOnError=True);

if (x instanceof List)
    {
    ((List) x).add(item);
    }

