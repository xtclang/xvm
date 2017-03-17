
public class Employee(String name, int age, Person manager)
    extends Person(String name, int age)
   {
   construct(Employee:struct struct, String name, int age, Person manager)
       {
       assert(manager.age > 16);

       construct Person(name, age);

       struct.manager = manager;
       }
   }

public class Person
    {
    construct(Person:struct struct, String name, int age)
       {
       assert(name.length() > 0);
       struct.name = name;
       struct.age  = age;
       }
    }

service Clock
    {
    Timer createTimer(Resolution res);
    }

service Timer
    {
    Cancellable addAlarm(Alarm alarm, DateTime timeToWakeup);

    Cancellable addAlarm(function Void () wakeup, DateTime timeToWakeup);
    CompletableFuture addAlarm(DateTime timeToWakeup);
    }

interface Alarm
    {
    Void wakeup();
    }

