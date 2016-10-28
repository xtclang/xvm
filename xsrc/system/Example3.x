interface List<T>
  {
  void add(T value);
  int size;
  T get(int i);
  }

{String get(int)} instanceof {Object get(int)} == true
{void add(String)} instanceof {void add(Object)} == true // only true because we need it to be

class Provider<T>
    {
    T get(int i);
    }

String stringExtractor(Provider<String> provider)
	{
	return provider.get(0);
	}

int intExtractor(Provider<int> provider)
	{
	return provider.get(0);
	}

List<Object> lo = new ArrayList();
String s = stringExtractor(lo);
int n = intExtractor(lo);

{void add(Object)} instanceof {void add(String)} == false // can I say Object o = "foo"

{Object get(int)} instanceof {String get(int)} == false // can I say String s = new Object();

class Logger<T>
    {
    void add(T t);
    }

// why is it LEGAL to pass a List<Object> to this method? i.e List<S> = List<O>
void stringLogger(Logger<String> logger)
	{
	logger.add("hi cam");
	}

void intLogger(Logger<int> logger)
	{
	logger.add(42);
	}

stringLogger(lo); // would be an error today ..
intLogger(lo);    // would be an error today ..


//

List<Object>
  void add(Object)
  Object get(int)

List<String>
  void add(String)
  String get(int)

List<int>
  void add(int)
  int get(int)


//

interface LogFile<T>
  {
  void log(T val);
  @ro T last;
  }

class SuperDuperLogFile<T>
    implements LogFile<T>
  {
  // ...
  }

interface Logger<T>
  {
  void log(T val);
  }

interface Recent<T>
  {
  @ro T last;
  }

void logSomeObject(Logger<Object> logger)
  {
  // this will work for Logger<Object>
  // this will work for Logger<int>
  // RTE for any other
  logger.log(3);
  }

void logSomeString(Logger<String>)
  {
  // this will work for Logger<Object>
  // this will work for Logger<String>
  // RTE for any other
  logger.log("Hello world!");
  }

void extractSomeObject(Recent<Object> history)
  {
  // this will work for any Recent<T>
  Object o = history.last;
  }

void extractSomeString(Recent<String> history)
  {
  // this will work for Recent<String>
  // RTE for any other
  String s = history.last;
  }

void test()
  {
  LogFile<Object> logAny = new SuperDuperLogFile<Object>;
  LogFile<String> logStr = new SuperDuperLogFile<String>;
  LogFile<int   > logInt = new SuperDuperLogFile<int   >;

  logSomeObject(logAny); // types match, no RTE
  logSomeObject(logStr); // auto-castable ("only b/c we need it to be"), but RTE!
  logSomeObject(logInt); // auto-castable ("only b/c we need it to be"), no RTE

  logSomeString(logAny); // auto-castable (naturally), no RTE
  logSomeString(logStr); // types match, no RTE
  logSomeString(logInt); // compile time error! (would also be an RTE)

  extractSomeObject(logAny); // types match, no RTE
  extractSomeObject(logStr); // auto-castable, no RTE
  extractSomeObject(logInt); // auto-castable, no RTE

  extractSomeString(logAny); // compile time error! (would also probably be an RTE)
  extractSomeString(logStr); // types match, no RTE
  extractSomeString(logInt); // compile time error! (would also be an RTE)
  }

// --- narrowing of return values

class StringBuilder
  {
  // return value is not actually compiled as "StringBuilder", but rather as "this-class"
  StringBuilder append(String s) {..}
  }

class SuperDuperStringBuilder
  {
  // implicitly: SuperDuperStringBuilder append(String s);
  }

