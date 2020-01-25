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

public class Person
    {
    public Person(String name, String phone)
        {
        setName(name);
        setPhone(phone);
        }

    private String name;
    private String phone;

    public String getName()
        {
        return name;
        }

    public void setName(String name)
        {
        assert name != null && name.length() > 0;
        }

    public String getPhone()
        {
        return phone;
        }

    public void setPhone(String phone)
        {
        this.phone = phone;
        }
    }

public class Constants
    {
    public static final Constants INSTANCE = new Constants();

    private Constants()
        {
        // initialization stuff goes here
        }

    private final String companyName;
    private final String companyEmail;

    public String getCompanyName()
        {
        return companyName;
        }

    public String getCompanyEmail()
        {
        return companyEmail;
        }
    }

public class Singleton
    {
    public static final Singleton INSTANCE = new Singleton();

    private Singleton()
        {
        // initialization stuff goes here
        }
    }

PageCounter.hit();

public class PageCounter
    {
    public static final PageCounter INSTANCE = new PageCounter();

    private PageCounter() {}

    private int count;

    synchronized public void setCount(int count)
        {
        this.count = count;
        }

    synchronized public int getCount()
        {
        return count;
        }

    synchronized public int hit()
        {
        return ++count;
        }
    }

// how to call the singleton
PageCounter.INSTANCE.hit();