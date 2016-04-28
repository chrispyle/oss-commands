package com.clearcapital.oss.executors;

import java.util.ArrayList;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.clearcapital.oss.commands.Command;
import com.clearcapital.oss.commands.CommandExecutionException;
import com.clearcapital.oss.java.StackHelpers;

/**
 * This class works in conjunction with {@link Command} to separate generation of Commands from their execution.
 * Mutations, for example, would include submission of write statements to a database driver. A {@link QueuedCommandExecutor}
 * represents a growing collection of Commands, which are executed once the entire set of mutations has been built.
 * 
 * Since this class implements AutoCloseable, it expects to be used in a try-with-resources block. A typical example
 * might look like:
 * 
 * <pre>
 * try (CommandExecutor executor = new QueuedCommandExecutor()) {
 *     // a series of calls generating a bunch of Bundles.
 *     executor.execute();
 * }
 * </pre>
 * 
 * @author eehlinger
 */
public class QueuedCommandExecutor implements CommandExecutor {

    private static Logger log = LoggerFactory.getLogger(QueuedCommandExecutor.class);

    private final Collection<Command> bundles = new ArrayList<Command>();

    /**
     * Create a BundleExecutor
     * 
     */
    public QueuedCommandExecutor() {
    }

    /**
     * Attempt to execute all of the stored {@link Command}s. If this method ever fails, you have the risk that some,
     * but not all, Commands were executed. There are several expected reasons that this method could fail:
     * 
     * 1: One of the Commands represents invalid database statements.
     * 
     * 2: One of the Commands throws CommandExecutionException.
     * 
     * 3: One of the Commands require activity which could fail for other reasons (network failure, for example).
     * 
     * @throws CommandExecutionException
     */
    @Override
    public void execute() throws CommandExecutionException {
        if (log.isTraceEnabled()) {
            log.trace("Running bundles from the following locations:");

            for (Command bundle : bundles) {
                log.trace("* " + bundle.getLocation());
            }
        }

        for (Command bundle : bundles) {
            bundle.execute();
        }

        bundles.clear();
    }

    /**
     * Close this executor. If it was not submitted to DSE, and there are bundles which were queued up, try to log a
     * warning message which shows as much information about where those bundles came from as possible.
     */
    @Override
    public void close() {
        if (bundles.size() != 0) {
            if (log.isWarnEnabled()) {
                try {
                    // Possible reasons for this log:
                    // (1) There was an exception thrown, in which case this entry is a Good Thing™.
                    // (2) You forgot to call executor.execute(), in which case this entry is still a Good Thing™,
                    // because it's
                    // helping you track down your bug.
                    String message = StackHelpers.getRelativeStackLocation() + ": Ignoring (similar to rolling back) "
                            + bundles.size() + " cassandra bundles, because this executor was never executed.\n";
                    message += "* Called from:" + StackHelpers.getRelativeStackLocation(1) + "\n";

                    message += "* Bundle locations:";
                    for (Command bundleBuilder : bundles) {
                        message += "  * " + bundleBuilder.getLocation() + "\n";
                    }
                    log.warn(message);
                } catch (Throwable e) {
                    // NOTE: there's really no such thing as a rollback. This really indicates that the caller simply
                    // failed to commit the changes.
                    log.warn("Failed while trying to warn about a \"rollback.\" I've done all I can. Sorry.");
                }
            }
        }
    }

    /**
     * Add {@code bundle} to the list of bundles to be executed later.
     * 
     * @param bundle
     *            a bundle to execute later.
     */
    @Override
    public void addCommand(Command bundle) {
        if (bundle != null) {
            bundles.add(bundle);
        }
    }

    public int getSize() {
        return bundles.size();
    }

}
