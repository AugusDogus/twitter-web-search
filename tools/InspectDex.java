import java.io.File;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.instruction.*;

class InspectDex {
    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        for (var entryName : container.getDexEntryNames()) {
            var entry = container.getEntry(entryName);
            if (entry == null) throw new IllegalStateException("Missing " + entryName);
            for (var cls : entry.getDexFile().getClasses()) {
                if (!cls.getType().contains(args[1])) continue;
                System.out.println("CLASS " + cls.getType() + " extends " + cls.getSuperclass());
                for (var method : cls.getMethods()) {
                    var impl = method.getImplementation();
                    if (impl == null) continue;
                    System.out.println("METHOD " + method + " registers=" + impl.getRegisterCount());
                    int index = 0, offset = 0;
                    for (var insn : impl.getInstructions()) {
                        var text = new StringBuilder(String.format("%3d @%04x %-24s", index++, offset, insn.getOpcode().name));
                        if (insn instanceof FiveRegisterInstruction i) text.append(" {").append(i.getRegisterC()).append(",").append(i.getRegisterD()).append(",").append(i.getRegisterE()).append(",").append(i.getRegisterF()).append(",").append(i.getRegisterG()).append("} count=").append(i.getRegisterCount());
                        else if (insn instanceof RegisterRangeInstruction i) text.append(" v").append(i.getStartRegister()).append(" count=").append(i.getRegisterCount());
                        else {
                            if (insn instanceof OneRegisterInstruction i) text.append(" v").append(i.getRegisterA());
                            if (insn instanceof TwoRegisterInstruction i) text.append(", v").append(i.getRegisterB());
                            if (insn instanceof ThreeRegisterInstruction i) text.append(", v").append(i.getRegisterC());
                        }
                        if (insn instanceof ReferenceInstruction i) text.append(" ").append(i.getReference());
                        if (insn instanceof WideLiteralInstruction i) text.append(" #").append(i.getWideLiteral());
                        if (insn instanceof OffsetInstruction i) text.append(" ->@").append(Integer.toHexString(offset + i.getCodeOffset()));
                        System.out.println(text);
                        offset += insn.getCodeUnits();
                    }
                }
            }
        }
    }
}
