package io.github.xboto.termrovercfaccess.cf;

import java.lang.reflect.Method;

import io.github.xboto.termrovercfaccess.Names;

final class TextFn {
    private TextFn() {
    }

    static Method find(ClassLoader cl, Names n) throws ReflectiveOperationException {
        Class<?> fn1 = Class.forName(n.fn1, false, cl);
        return Class.forName(n.text, false, cl).getDeclaredMethod("b", String.class,
                Class.forName(n.modifier, false, cl), long.class, long.class,
                Class.forName(n.fontWeight, false, cl), Class.forName(n.fontFamily, false, cl),
                long.class, Class.forName(n.textDecoration, false, cl),
                Class.forName(n.textAlign, false, cl), long.class, int.class, boolean.class,
                int.class, int.class, fn1, Class.forName(n.textStyle, false, cl),
                Class.forName(n.composer, false, cl), int.class, int.class, int.class);
    }
}
