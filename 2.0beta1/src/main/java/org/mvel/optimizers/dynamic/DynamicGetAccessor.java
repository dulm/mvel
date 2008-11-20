package org.mvel.optimizers.dynamic;

import org.mvel.compiler.Accessor;
import org.mvel.integration.VariableResolverFactory;
import org.mvel.optimizers.OptimizerFactory;
import org.mvel.optimizers.AccessorOptimizer;

import static java.lang.System.currentTimeMillis;

//todo: de-optimize in this class.
public class DynamicGetAccessor implements DynamicAccessor {
    private char[] property;
    private long stamp;
    private int type;

    private int runcount;

    private boolean opt = false;

    private Accessor _safeAccessor;
    private Accessor _accessor;

    public DynamicGetAccessor(char[] property, int type, Accessor _accessor) {
        this._safeAccessor = this._accessor = _accessor;
        this.type = type;
        this.property = property;
        stamp = currentTimeMillis();
    }

    public Object getValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory) {
        if (!opt) {
            if (++runcount > DynamicOptimizer.tenuringThreshold) {
                if ((currentTimeMillis() - stamp) < DynamicOptimizer.timeSpan) {
                    opt = true;
                    return optimize(ctx, elCtx, variableFactory);
                }
                else {
                    runcount = 0;
                    stamp = currentTimeMillis();
                }
            }
        }

        return _accessor.getValue(ctx, elCtx, variableFactory);
    }

    public Object setValue(Object ctx, Object elCtx, VariableResolverFactory variableFactory, Object value) {
        runcount++;
        return _accessor.setValue(ctx, elCtx, variableFactory, value);
    }

    private Object optimize(Object ctx, Object elCtx, VariableResolverFactory variableResolverFactory) {

        if (DynamicOptimizer.classLoader.isOverloaded()) {
            DynamicOptimizer.enforceTenureLimit();
        }

        AccessorOptimizer ao = OptimizerFactory.getAccessorCompiler("ASM");
        switch (type) {
            case DynamicOptimizer.REGULAR_ACCESSOR:
                _accessor = ao.optimizeAccessor(property, ctx, elCtx, variableResolverFactory, false);
                return ao.getResultOptPass();
            case DynamicOptimizer.OBJ_CREATION:
                _accessor = ao.optimizeObjectCreation(property, ctx, elCtx, variableResolverFactory);
                return _accessor.getValue(ctx, elCtx, variableResolverFactory);
            case DynamicOptimizer.COLLECTION:
                _accessor = ao.optimizeCollection(null, null, property, ctx, elCtx, variableResolverFactory);
                return _accessor.getValue(ctx, elCtx, variableResolverFactory);
            case DynamicOptimizer.FOLD:
                _accessor = ao.optimizeFold(property, ctx, elCtx, variableResolverFactory);
                return ao.getResultOptPass();
        }
        return null;
    }

    public void deoptimize() {
        this._accessor = this._safeAccessor;
        opt = false;
        runcount = 0;
        stamp = currentTimeMillis();
    }

    public long getStamp() {
        return stamp;
    }

    public int getRuncount() {
        return runcount;
    }
}