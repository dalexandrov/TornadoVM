package tornado.graal.phases;

import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.tiers.MidTierContext;
import com.oracle.graal.phases.tiers.TargetProvider;
import com.oracle.graal.phases.util.Providers;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.meta.Meta;

public class TornadoMidTierContext extends MidTierContext {

    protected final ResolvedJavaMethod method;
    protected final Object[] args;
    protected final Meta meta;

    public TornadoMidTierContext(
            Providers copyFrom,
            TargetProvider target,
            OptimisticOptimizations optimisticOpts,
            ProfilingInfo profilingInfo,
            ResolvedJavaMethod method, Object[] args, Meta meta) {
        super(copyFrom, target, optimisticOpts, profilingInfo);
        this.method = method;
        this.args = args;
        this.meta = meta;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }

    public boolean hasArgs() {
        return args != null;
    }

    public Object getArg(int index) {
        return args[index];
    }

    public int getNumArgs() {
        return (hasArgs()) ? args.length : 0;
    }

    public Meta getMeta() {
        return meta;
    }

}
