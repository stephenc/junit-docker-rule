package pl.domzal.junit.docker.rule;

import java.util.HashSet;
import java.util.Set;
import org.junit.Rule;

/**
 * Container stop options. If not redefined active options are {@link #STOP}, {@link #REMOVE}.
 */
public enum StopOption {

    /**
     * Keep container instance at end. Opposite of {@link #REMOVE}.
     */
    KEEP,
    /**
    * Remove container instance at end. Opposite of {@link #KEEP}.
    */
    REMOVE,
    /**
     * Stop at end. Opposite of {@link #KILL}
     */
    STOP,
    /**
     * Kill at end. Opposite of {@link #STOP}.
     */
    KILL,
    /**
     * If the container instance fails to start in {@link DockerRule#before()} then this option will hold the
     * container until {@link DockerRule#after()}. A less resource leaky version of {@link #KEEP}. Typical use case
     * is where you need to access the logs if wait conditions fail and the {@link DockerRule} is not annotated with
     * {@link Rule} (because the test will not run if the rule fails to start.
     */
    INSPECTING;

    static class StopOptionSet {

        private final Set<StopOption> currentOptions = new HashSet<>();

        StopOptionSet() {
            currentOptions.add(StopOption.STOP);
            currentOptions.add(StopOption.REMOVE);
        }

        public void setOptions(StopOption... newOptions) {
            for (StopOption option : newOptions) {
                currentOptions.add(option);
                if (StopOption.KEEP.equals(option)) {
                    currentOptions.remove(StopOption.REMOVE);
                } else if (StopOption.REMOVE.equals(option)) {
                    currentOptions.remove(StopOption.KEEP);
                } else if (StopOption.STOP.equals(option)) {
                    currentOptions.remove(StopOption.KILL);
                } else if (StopOption.KILL.equals(option)) {
                    currentOptions.remove(StopOption.STOP);
                }
            }
        }

        public boolean contains(StopOption stopOption) {
            return currentOptions.contains(stopOption);
        }
    }
}
