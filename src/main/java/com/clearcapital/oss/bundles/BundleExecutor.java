package com.clearcapital.oss.bundles;

/**
 * Interface allowing a bundle execution strategy to be pluggable.
 * 
 * @author eehlinger
 * 
 */
public interface BundleExecutor extends AutoCloseable {

    /**
     * Add a command to be executed according to the implementing class's execution strategy.
     * 
     * @param command
     *            The command to be executed. null is acceptable; the implementing class must ignore null bundles.
     * 
     * @throws CoreException
     */
    public void addCommand(Command command) throws CommandExecutionException;

    public void execute() throws CommandExecutionException;

    public void close() throws CommandExecutionException;
}
