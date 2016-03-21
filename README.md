![Logo](https://www.clearcapital.com/wp-content/uploads/2015/02/Clear-Capital@2x.png)
# Commands

## What is it?

The Commands library helps you to implement *near*-transactional
behavior on top of database(s) which do not provide transactions.

An Executor is a collection of implementations of the Command pattern,
intended for use with Java's
[try-with-resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)
feature to assist in writing *near*-transactional code. The idea is
that, by separating the *calculation* and *execution* of
non-transactional mutations, we can delay the *executions* until such
time as we can provide a reasonable guarantee that we will submit all
of them without exceptions causing issues.  The pattern for using
Commands looks something like this:

```java
try (CommandExecutor executor = new QueuedCommandExecutor()) {
     // a series of calls generating a bunch of Commands.
     executor.execute();
}
```

Note that you must explicitly call executor.execute(), or else the
executor will **IGNORE** all of the generated ```Commands```, and the
changes represented by those ```Commands``` will not
occur. ```QueuedCommandExecutor``` tracks whether execute() has
been called, and logs warnings if you call close() without calling
execute() first.

This *helps* guarantee consistent sets of changes, e.g., writes to a
database or set of databases which cannot provide transactions with
roll-back. If you are building a series of related commands, and an
exception is thrown in the middle of that process, you don't want any
of the writes to occur, because that could leave the data in an
inconsistent state:

```java
try (CommandExecutor executor = new QueuedCommandExecutor()) {
     executor.addBundle(generateCommand('A'));
     throw new Exception();
     executor.addBundle(generateCommand('B'));
     executor.execute();
}
```

In this example, A and B must be executed together, or not at all. If
there's an exception, we don't want either command to execute. What
will happen here is that Command 'A' will be added to "executor," then
the exception will be thrown, bypassing the generation of Command 'B'
and the call to ```executor.execute()```. ```executor.close()``` will
then be called by the try-with-resources block, and
```DelgatingCommandExecutor``` will write a warning to the log that it
is ignoring Command "A." The Commands library attempts to make this log
message as useful as possible, including, for example, the method
locations where the ```Commands``` were generated.

## Why might I need it?

Here are a few scenarios under which you might consider using Commands:

- You need to write a bunch of data to a Cassanra database. Cassandra
  does not provide transactions in the traditional RDBMS sense of being
  able to do a ```BEGIN TRANSACTION```, followed by a series of reads
  and writes, followed by a ```COMMIT```. You can create batch
  statements, and if you submit a batch statement, Cassandra guarantees
  that the batch statement will eventually become consistent. Batch
  statements in Cassandra are collections of regular statements. This
  seems ideal, except for two things:

  - Batch statements are considerably slower than the equivalent
    non-batched statement sequences. Roughly
    [30% slower](http://www.datastax.com/dev/blog/atomic-batches-in-cassandra-1-2)

  - Not every type of write in Cassandra is batchable.

- You've built a system on a paid-support version of Cassandra in
  order to take advantage of features like SOLR integration, but now
  you're finding that licensing costs are enough of a burden that you
  would like to split your Cassandra ring into two rings: one which
  uses the SOLR integration provided by the paid-support version, and
  one which only depends on purely open-source Cassandra. This causes
  a bit of a problem for all of your batch statements, though: because
  you're talking to two completely independent rings, your batche
  statements must be split in two. So the databases can no longer
  provide you with transactional behavior.

- You have valid, inescapable business reasons pushing you to write to
  two RDBMS systems which individually provide you with ACID
  transactions, but which are from different vendors, and you need to
  write to them in a manner which minimizes the risk that the
  databases will fall out of sync.

- You are writing a middle tier in an *n*-tier architecture, which
  depends on issuing ```PUT``` or ```POST``` calls to disparate
  microservices in order to persist your tier's changes.

In all of these cases, it's not exactly easy to implement a
transactional layer on top of the non-transactional layer, and it's
also likely acceptable to accept *some* risk of inconsistency, but not
*all* of the risk.

## What's Provided

There isn't really much provided by the Commands library; it's more about
supporting the *paradigm* of decoupling the generation of command generation
from the execution of those commands.  The entire library consists of
only *five* java modules (excluding tests):

- ```Command``` - this is an ```interface``` which you must implement
  to take advantage of the Bundle library.  It provides one method for
  determining where the Command was generated and another for actually
  executing the Command. We purposefully left out the implementations
  of Command which we use, because they are specific to Cassandra, and
  adding a dependency on Cassandra didn't seem right for this library.
  If you'd like to see a concrete implementation, please check out our
  oss-datastax-helpers library and look for ```CassandraCommand```.

- ```BundleExecutionException``` - this is the only type of exception which
  is allowed from ```Command.execute()```

- CommandExecutor - this is an ```interface``` which allows you to
  choose a policy for *when* to execute ```Commands```s. Two
  implementations are provided, though you will generally want to use
  ```QueuedCommandExecutor``` outside of unit tests.

- ```QueuedCommandExecutor``` - This implementation of
  ```CommandExecutor``` is really the heart and soul of this
  library. This ```class``` supports the delay of execution which
  brings value.

- ```ImmediateCommandExecutor``` - This is useful for writing unit
  tests for the layers of code which need a ```CommandExecutor``` to
  operate. This might include data access layers, for example.

## Notes

- Assuming your software is layered so that you have a data-access
  layer near the "bottom" of your stack, talking to the database, and
  that you are using ```Commands``` to assist in providing consistency,
  it is generally very bad form to create an executor at the DAO or
  Table layers, because it is rare that data access layers can
  understand the full context of their actions and the rules around
  the timing of their writes. In general, it is preferable to have
  transactions (think RESTful API call implementations, or a step in a
  command-line utility) designed so that you can have a single
  executor which executes at the end. So the pattern becomes: read,
  generate ```Command``` objects, execute.

- Further, although this approach is cleaner than having dozens of try
  blocks and trying to manage every possible failure, where an
  exception during the mixed calculation and write phase could result
  in some writes being submitted to database drivers, it is not a
  panacea. For example, it is possible for an exception to occur just
  during the ```CommandExecutor.execute()``` method. This approach
  reduces the window for inconsistent writes to a few likely causes:
 
  - a query is improperly generated, resulting in a bad-request type
    of exception from a database driver. Mitigate this risk by
    ensuring that all queries have proper unit tests.
  
  - a database server goes offline in the middle of
    ```CommandExecutor.execute()```.

  - the entire Java container holding the ```CommandExecutor``` crashes
    in the middle of a call to ```CommandExecutor.execute()```, perhaps
    due to a poorly-timed out of memory condition.
  
## Room for improvement / Future plans:

- It is conceivable that we could provide more safety by updating the
  ```Command``` interface so that it has an ```undo()``` method, which
  we would call if any of the ```Command.execute()``` calls from
  ```QueuedCommandExecutor``` throw an exception. Of course, with this
  sort of behavior, you could still get transient inconsistency, and
  there are still issues like, "well, what happens if the undo() method
  throws?"

- It is *very* conceivable that we could provide better guarantees by
  pushing all of the ```Command```s to a message queue rather than
  directly executing them. The message queue would then be responsible
  for providing a strong guarantee that the list of ```Command```s
  would all have their ```execute()``` methods called. This may be
  simpler than adding ```undo()```, because it would mean that failed
  ```execute()```s would be retried, rather than attempting to roll
  back the writes from the same ```Command``` list. This would involve
  writing something like a ```MessageQueueCommandExecutor```. It may be
  worthwhile to keep such a thing in a separate library, or a set of
  other libraries, which integrate this package with varying message
  queue systems.

## Related Packages

- *oss-cassandra-helpers* [//] # (../oss-cassandra-helpers/README.md)
