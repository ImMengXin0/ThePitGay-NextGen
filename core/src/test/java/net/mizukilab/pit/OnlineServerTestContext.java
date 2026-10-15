package net.mizukilab.pit;

import net.bytebuddy.jar.asm.*;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import java.lang.reflect.Field;
import java.util.*;

/** Exact JVM descriptors avoid Mockito confusing Bukkit 1.8's return-only overloads. */
public final class OnlineServerTestContext implements AutoCloseable {
    private final Field serverField;
    private final Object previous;
    private final Object server;
    public OnlineServerTestContext(BukkitScheduler scheduler, PluginManager manager) throws Exception {
        String name="net/mizukilab/pit/TestServerSurface";
        ClassWriter writer=new ClassWriter(0);
        writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",new String[]{"org/bukkit/Server"});
        var ctor=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD,0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(1,1); ctor.visitEnd();
        Map<String,String[]> fields=new LinkedHashMap<>();
        fields.put("players",new String[]{"getOnlinePlayers","Ljava/util/Collection;"});
        fields.put("legacyPlayers",new String[]{"getOnlinePlayers","[Lorg/bukkit/entity/Player;"});
        fields.put("scheduler",new String[]{"getScheduler","Lorg/bukkit/scheduler/BukkitScheduler;"});
        fields.put("manager",new String[]{"getPluginManager","Lorg/bukkit/plugin/PluginManager;"});
        fields.put("primary",new String[]{"isPrimaryThread","Z"});
        for(var field:fields.entrySet()) {
            String descriptor=field.getValue()[1];
            writer.visitField(Opcodes.ACC_PUBLIC,field.getKey(),descriptor,null,null).visitEnd();
            var method=writer.visitMethod(Opcodes.ACC_PUBLIC,field.getValue()[0],"()"+descriptor,null,null);
            method.visitCode(); method.visitVarInsn(Opcodes.ALOAD,0);
            method.visitFieldInsn(Opcodes.GETFIELD,name,field.getKey(),descriptor);
            method.visitInsn(descriptor.equals("Z")?Opcodes.IRETURN:Opcodes.ARETURN);
            method.visitMaxs(1,1); method.visitEnd();
        }
        writer.visitEnd(); byte[] bytes=writer.toByteArray();
        Class<?> type=new ClassLoader(Server.class.getClassLoader()) {
            Class<?> define() { return defineClass(null,bytes,0,bytes.length); }
        }.define();
        server=type.getConstructor().newInstance();
        type.getField("scheduler").set(server,scheduler); type.getField("manager").set(server,manager);
        primary(true); players(List.of());
        serverField=Bukkit.class.getDeclaredField("server"); serverField.setAccessible(true);
        previous=serverField.get(null); serverField.set(null,server);
    }
    public void players(List<Player> players) throws Exception {
        server.getClass().getField("players").set(server,players);
        server.getClass().getField("legacyPlayers").set(server,players.toArray(new Player[0]));
    }
    public void primary(boolean primary) throws Exception { server.getClass().getField("primary").setBoolean(server,primary); }
    @Override public void close() throws Exception { serverField.set(null,previous); }
}
