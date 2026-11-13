package io.github.xboto.termrovercfaccess;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.Socket;

public final class Names {
    public final String version;

    public final String unit;
    public final String fn0;
    public final String fn1;
    public final String modifier;
    public final String modifierCompanion;
    public final String composer;
    public final String switchColors;
    public final String shape;
    public final String continuation;

    public final String metaReader;
    public final String sessionsReader;
    public final String cctBackend;
    public final String cctUpload;
    public final String cctRequest;
    public final String cctResponse;
    public final String crashWriter;
    public final String crashWriterMethod;
    public final String aboutReader;
    public final String switchCallback;
    public final String rowToggle;

    public final String themes;
    public final String theme;
    public final String themeChrome;

    public final String conn;
    public final String connSocket;
    public final String connCancel;
    public final String connSuspend;
    public final String connSuspendFn;
    public final String transportFactory;
    public final int directKind;
    public final String socketTransport;

    public final String formFields;
    public final String formField;
    public final String fieldRestart;
    public final String rows;
    public final String toggleRow;
    public final int graphicsLayerFloats;
    public final int graphicsLayerAlphaMask;
    public final String authRow;
    public final String authOption;
    public final String authEnum;
    public final String transportEnum;
    public final String transportPickerOwner;
    public final String transportPicker;

    public final String clipOwner;
    public final String clip;
    public final String borderOwner;
    public final String border;
    public final String rectShapeOwner;
    public final String rectShape;
    public final String roundedOwner;
    public final String rounded;
    public final String spOwner;
    public final String sp;
    public final String switchColorsOwner;
    public final String switchColorsFn;
    public final String fontWeight;
    public final String fontFamily;
    public final String textDecoration;
    public final String textAlign;
    public final String textStyle;
    public final String text;
    public final String colors;

    public final String profile;
    public final String menu;
    public final String menuRow;
    public final String menuItems;
    public final String session;
    public final int sessionParams;
    public final String switcher;
    public final boolean switcherNoteArg;
    public final String note;
    public final String popup;
    public final String quickSettings;
    public final String quickSettingsBody;
    public final String quickSettingsHost;

    private Names(Builder b) {
        version = b.version;
        unit = b.unit; fn0 = b.fn0; fn1 = b.fn1; modifier = b.modifier;
        modifierCompanion = b.modifierCompanion; composer = b.composer;
        switchColors = b.switchColors; shape = b.shape; continuation = b.continuation;
        metaReader = b.metaReader; sessionsReader = b.sessionsReader; cctBackend = b.cctBackend;
        cctUpload = b.cctUpload; cctRequest = b.cctRequest; cctResponse = b.cctResponse;
        crashWriter = b.crashWriter; crashWriterMethod = b.crashWriterMethod;
        aboutReader = b.aboutReader; switchCallback = b.switchCallback; rowToggle = b.rowToggle;
        themes = b.themes; theme = b.theme; themeChrome = b.themeChrome;
        conn = b.conn; connSocket = b.connSocket; connCancel = b.connCancel;
        connSuspend = b.connSuspend; connSuspendFn = b.connSuspendFn;
        transportFactory = b.transportFactory; directKind = b.directKind;
        socketTransport = b.socketTransport;
        formFields = b.formFields; formField = b.formField; fieldRestart = b.fieldRestart;
        rows = b.rows; toggleRow = b.toggleRow; graphicsLayerFloats = b.graphicsLayerFloats;
        graphicsLayerAlphaMask = b.graphicsLayerAlphaMask;
        authRow = b.authRow; authOption = b.authOption; authEnum = b.authEnum;
        transportEnum = b.transportEnum; transportPickerOwner = b.transportPickerOwner;
        transportPicker = b.transportPicker;
        clipOwner = b.clipOwner; clip = b.clip; borderOwner = b.borderOwner; border = b.border;
        rectShapeOwner = b.rectShapeOwner; rectShape = b.rectShape; roundedOwner = b.roundedOwner;
        rounded = b.rounded; spOwner = b.spOwner; sp = b.sp; switchColorsOwner = b.switchColorsOwner;
        switchColorsFn = b.switchColorsFn; fontWeight = b.fontWeight; fontFamily = b.fontFamily;
        textDecoration = b.textDecoration; textAlign = b.textAlign; textStyle = b.textStyle;
        text = b.text; colors = b.colors;
        profile = b.profile; menu = b.menu; menuRow = b.menuRow; menuItems = b.menuItems;
        session = b.session; sessionParams = b.sessionParams; switcher = b.switcher;
        switcherNoteArg = b.switcherNoteArg; note = b.note; popup = b.popup;
        quickSettings = b.quickSettings; quickSettingsBody = b.quickSettingsBody;
        quickSettingsHost = b.quickSettingsHost;
    }

    static final Names V1_3_1 = new Builder() {{
        version = "1.3.1";
        unit = "jf5"; fn0 = "sy1"; fn1 = "ty1"; modifier = "v53"; modifierCompanion = "s53";
        composer = "xr0"; switchColors = "nx4"; shape = "ik4"; continuation = "ow0";
        metaReader = "az5"; sessionsReader = "qy2"; cctBackend = "ma"; cctUpload = "e";
        cctRequest = "zg0"; cctResponse = "tj0"; crashWriter = "oz4"; crashWriterMethod = "e";
        aboutReader = "mj4"; switchCallback = "qj"; rowToggle = "zj";
        themes = "z65"; theme = "uz4"; themeChrome = "ll0";
        conn = "rs4"; connSocket = "C"; connCancel = "F"; connSuspend = "G"; connSuspendFn = "nr4";
        transportFactory = "uk"; directKind = 10; socketTransport = "ip4";
        formFields = "e06"; formField = "q"; fieldRestart = "n52"; rows = "w25"; toggleRow = "Z";
        graphicsLayerFloats = 4; graphicsLayerAlphaMask = 1 | 2 | 32 | 2048 | 4096;
        authRow = "k"; authOption = "j"; authEnum = "t10"; transportEnum = "gt0";
        transportPickerOwner = "i23"; transportPicker = "e";
        clipOwner = "dl2"; clip = "v"; borderOwner = "ug5"; border = "g"; rectShapeOwner = "ug5";
        rectShape = "i"; roundedOwner = "j64"; rounded = "b"; spOwner = "v65"; sp = "c";
        switchColorsOwner = "kg2"; switchColorsFn = "m"; fontWeight = "gv1"; fontFamily = "bu1";
        textDecoration = "q35"; textAlign = "j35"; textStyle = "q65"; text = "r55"; colors = "si3";
        profile = "l62"; menu = "wo"; menuRow = "p"; menuItems = "n";
        session = "f"; sessionParams = 34; switcher = "f0"; switcherNoteArg = false;
        note = "h0"; popup = "z15";
    }}.build();

    static final Names V1_4_0 = new Builder() {{
        version = "1.4.0";
        unit = "l16"; fn0 = "u22"; fn1 = "v22"; modifier = "uq3"; modifierCompanion = "rq3";
        composer = "ws0"; switchColors = "fk5"; shape = "y55"; continuation = "mx0";
        metaReader = "al6"; sessionsReader = "qj3"; cctBackend = "ib"; cctUpload = "d";
        cctRequest = "yh0"; cctResponse = "sk0"; crashWriter = "r95"; crashWriterMethod = "a";
        aboutReader = "e55"; switchCallback = "mk"; rowToggle = "vk";
        themes = "xs5"; theme = "nm5"; themeChrome = "km0";
        conn = "mf5"; connSocket = "D"; connCancel = "I"; connSuspend = "J"; connSuspendFn = "ge5";
        transportFactory = "ql"; directKind = -1; socketTransport = "pb5";
        formFields = "k17"; formField = "s"; fieldRestart = "vq2"; rows = "ia5"; toggleRow = "Z";
        graphicsLayerFloats = 5; graphicsLayerAlphaMask = 1 | 2 | 0x20 | 0x100 | 0x800 | 0x1000;
        authRow = "l"; authOption = "k"; authEnum = "r20"; transportEnum = "eu0";
        transportPickerOwner = "iz5"; transportPicker = "a";
        clipOwner = "iz5"; clip = "s"; borderOwner = "ef6"; border = "q"; rectShapeOwner = "xb6";
        rectShape = "e"; roundedOwner = "tr4"; rounded = "b"; spOwner = "f95"; sp = "m";
        switchColorsOwner = "jm6"; switchColorsFn = "b"; fontWeight = "fz1"; fontFamily = "ay1";
        textDecoration = "dq5"; textAlign = "wp5"; textStyle = "qs5"; text = "vr5"; colors = "t34";
        profile = "tr2"; menu = "qx0"; menuRow = "o"; menuItems = "m";
        session = "f"; sessionParams = 36; switcher = "f0"; switcherNoteArg = true;
        quickSettings = "y"; quickSettingsBody = "x"; quickSettingsHost = "pa";
    }}.build();

    static final Names V1_4_1 = new Builder() {{
        version = "1.4.1";
        unit = "q16"; fn0 = "z22"; fn1 = "a32"; modifier = "zq3"; modifierCompanion = "wq3";
        composer = "bt0"; switchColors = "jk5"; shape = "d65"; continuation = "sx0";
        metaReader = "fl6"; sessionsReader = "vj3"; cctBackend = "ib"; cctUpload = "d";
        cctRequest = "di0"; cctResponse = "xk0"; crashWriter = "w95"; crashWriterMethod = "a";
        aboutReader = "j55"; switchCallback = "sk"; rowToggle = "bl";
        themes = "ct5"; theme = "sm5"; themeChrome = "pm0";
        conn = "qf5"; connSocket = "D"; connCancel = "I"; connSuspend = "J"; connSuspendFn = "ke5";
        transportFactory = "wl"; directKind = -1; socketTransport = "ub5";
        formFields = "p17"; formField = "s"; fieldRestart = "ar2"; rows = "na5"; toggleRow = "Z";
        graphicsLayerFloats = 5; graphicsLayerAlphaMask = 1 | 2 | 0x20 | 0x100 | 0x800 | 0x1000;
        authRow = "l"; authOption = "k"; authEnum = "w20"; transportEnum = "ju0";
        transportPickerOwner = "nz5"; transportPicker = "a";
        clipOwner = "nz5"; clip = "s"; borderOwner = "jf6"; border = "q"; rectShapeOwner = "cc6";
        rectShape = "e"; roundedOwner = "yr4"; rounded = "b"; spOwner = "k95"; sp = "m";
        switchColorsOwner = "om6"; switchColorsFn = "b"; fontWeight = "kz1"; fontFamily = "fy1";
        textDecoration = "iq5"; textAlign = "bq5"; textStyle = "vs5"; text = "as5"; colors = "z34";
        profile = "yr2"; menu = "wx0"; menuRow = "o"; menuItems = "m";
        session = "f"; sessionParams = 36; switcher = "f0"; switcherNoteArg = true;
        quickSettings = "y"; quickSettingsBody = "x"; quickSettingsHost = "pa";
    }}.build();

    static final Names V1_4_2 = new Builder() {{
        version = "1.4.2";
        unit = "m36"; fn0 = "i32"; fn1 = "j32"; modifier = "zs3"; modifierCompanion = "ws3";
        composer = "ht0"; switchColors = "em5"; shape = "a85"; continuation = "xx0";
        metaReader = "cn6"; sessionsReader = "sl3"; cctBackend = "mb"; cctUpload = "d";
        cctRequest = "ii0"; cctResponse = "cl0"; crashWriter = "vb5"; crashWriterMethod = "a";
        aboutReader = "i75"; switchCallback = "el"; rowToggle = "yk";
        themes = "yu5"; theme = "lo5"; themeChrome = "um0";
        conn = "lh5"; connSocket = "D"; connCancel = "I"; connSuspend = "J"; connSuspendFn = "fg5";
        transportFactory = "bm"; directKind = -1; socketTransport = "td5";
        formFields = "fq0"; formField = "s"; fieldRestart = "ps2"; rows = "mc5"; toggleRow = "Z";
        graphicsLayerFloats = 5; graphicsLayerAlphaMask = 1 | 2 | 0x20 | 0x100 | 0x800 | 0x1000;
        authRow = "l"; authOption = "k"; authEnum = "b30"; transportEnum = "pu0";
        transportPickerOwner = "x46"; transportPicker = "a";
        clipOwner = "x46"; clip = "o"; borderOwner = "ho6"; border = "j"; rectShapeOwner = "ij6";
        rectShape = "i"; roundedOwner = "rt4"; rounded = "b"; spOwner = "jb5"; sp = "m";
        switchColorsOwner = "lo6"; switchColorsFn = "b"; fontWeight = "vz1"; fontFamily = "qy1";
        textDecoration = "es5"; textAlign = "xr5"; textStyle = "ru5"; text = "wt5"; colors = "z54";
        profile = "nt2"; menu = "u31"; menuRow = "R"; menuItems = "P";
        session = "f"; sessionParams = 36; switcher = "f0"; switcherNoteArg = true;
        quickSettings = "y"; quickSettingsBody = "x"; quickSettingsHost = "ta";
    }}.build();

    static final Names V1_5_0 = new Builder() {{
        version = "1.5.0";
        unit = "fq6"; fn0 = "f72"; fn1 = "g72"; modifier = "p14"; modifierCompanion = "m14";
        composer = "nu0"; switchColors = "l86"; shape = "iu5"; continuation = "ez0";
        metaReader = "y97"; sessionsReader = "ls3"; cctBackend = "qb"; cctUpload = "c";
        cctRequest = "zi0"; cctResponse = "ul0"; crashWriter = "xw4"; crashWriterMethod = "c";
        aboutReader = "pt5"; switchCallback = "ll"; rowToggle = "fl";
        themes = "jh6"; theme = "ta6"; themeChrome = "mn0";
        conn = "i36"; connSocket = "D"; connCancel = "I"; connSuspend = "J"; connSuspendFn = "c26";
        transportFactory = "im"; directKind = -1; socketTransport = "nz5";
        formFields = "g61"; formField = "m"; fieldRestart = "dz2"; rows = "k15"; toggleRow = "a0";
        graphicsLayerFloats = 5; graphicsLayerAlphaMask = 1 | 2 | 0x20 | 0x100 | 0x800 | 0x1000;
        authRow = "c"; authOption = "b"; authEnum = "k30"; transportEnum = "xv0";
        transportPickerOwner = "gv7"; transportPicker = "b";
        clipOwner = "me0"; clip = "p"; borderOwner = "g61"; border = "S"; rectShapeOwner = "fr0";
        rectShape = "y"; roundedOwner = "sf5"; rounded = "b"; spOwner = "tw4"; sp = "r";
        switchColorsOwner = "cq4"; switchColorsFn = "c"; fontWeight = "p32"; fontFamily = "k22";
        textDecoration = "le6"; textAlign = "ee6"; textStyle = "zg6"; text = "eg6"; colors = "te4";
        profile = "yz2"; menu = "cy3"; menuRow = "t"; menuItems = "r";
        session = "f"; sessionParams = 38; switcher = "g0"; switcherNoteArg = true;

        quickSettings = "z"; quickSettingsBody = "y"; quickSettingsHost = "xa";
    }}.build();

    private static final Names[] ALL = {V1_5_0, V1_4_2, V1_4_1, V1_4_0, V1_3_1};

    public static Names detect(ClassLoader cl) {
        for (Names n : ALL) {
            if (matches(n, cl)) return n;
        }
        return null;
    }

    private static boolean matches(Names n, ClassLoader cl) {
        try {
            Class<?> unit = Class.forName(n.unit, false, cl);
            Field a = unit.getField("a");
            if (!Modifier.isStatic(a.getModifiers()) || a.getType() != unit) return false;
            Class<?> conn = Class.forName(n.conn, false, cl);
            if (conn.getField("o").getType() != String.class) return false;
            if (conn.getField(n.connSocket).getType() != Socket.class) return false;
            if (conn.getField(n.connCancel).getType() != boolean.class) return false;
            Class.forName(n.socketTransport, false, cl).getConstructor(Socket.class);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static class Builder {
        String version, unit, fn0, fn1, modifier, modifierCompanion, composer, switchColors, shape,
                continuation, metaReader, sessionsReader, cctBackend, cctUpload, cctRequest, cctResponse,
                crashWriter, crashWriterMethod, aboutReader, switchCallback, rowToggle, themes, theme,
                themeChrome, conn, connSocket, connCancel, connSuspend, connSuspendFn, transportFactory,
                socketTransport, formFields, formField, fieldRestart, rows, toggleRow, authRow, authOption,
                authEnum, transportEnum, transportPickerOwner, transportPicker, clipOwner, clip,
                borderOwner, border, rectShapeOwner, rectShape, roundedOwner, rounded, spOwner, sp,
                switchColorsOwner, switchColorsFn, fontWeight, fontFamily, textDecoration, textAlign,
                textStyle, text, colors, profile, menu, menuRow, menuItems, session, switcher, note, popup,
                quickSettings, quickSettingsBody, quickSettingsHost;
        int directKind, graphicsLayerFloats, graphicsLayerAlphaMask, sessionParams;
        boolean switcherNoteArg;

        Names build() {
            return new Names(this);
        }
    }
}
