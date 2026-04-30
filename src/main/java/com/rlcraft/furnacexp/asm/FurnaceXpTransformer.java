package com.rlcraft.furnacexp.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

public class FurnaceXpTransformer implements IClassTransformer {
    private static final String HOOKS = "com/rlcraft/furnacexp/asm/FurnaceXpHooks";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        if ("net.minecraft.tileentity.TileEntityFurnace".equals(transformedName)) {
            return transformFurnace(basicClass);
        }

        if ("net.minecraft.inventory.SlotFurnaceOutput".equals(transformedName)) {
            return transformSlotFurnaceOutput(basicClass);
        }


        return basicClass;
    }

    private byte[] transformFurnace(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public void visitEnd() {
                FieldVisitor fv = super.visitField(Opcodes.ACC_PUBLIC, "rlcraftfurnacefix$storedXp", "D", null, null);
                if (fv != null) {
                    fv.visitEnd();
                }
                super.visitEnd();
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                if (("removeStackFromSlot".equals(name) || "func_70304_b".equals(name)) && "(I)Lnet/minecraft/item/ItemStack;".equals(desc)) {
                    return new AdviceAdapter(Opcodes.ASM5, mv, access, name, desc) {
                        private int stackLocal = -1;

                        @Override
                        protected void onMethodExit(int opcode) {
                            if (opcode == ARETURN) {
                                if (stackLocal == -1) {
                                    stackLocal = newLocal(Type.getObjectType("net/minecraft/item/ItemStack"));
                                }
                                storeLocal(stackLocal);
                                loadThis();
                                loadArg(0);
                                loadLocal(stackLocal);
                                invokeStatic(Type.getObjectType(HOOKS), new Method("onFurnaceRemoveStackFromSlot", "(Lnet/minecraft/tileentity/TileEntityFurnace;ILnet/minecraft/item/ItemStack;)V"));
                                loadLocal(stackLocal);
                            }
                        }
                    };
                }


                if (("decrStackSize".equals(name) || "func_70298_a".equals(name)) && "(II)Lnet/minecraft/item/ItemStack;".equals(desc)) {
                    return new AdviceAdapter(Opcodes.ASM5, mv, access, name, desc) {
                        private int stackLocal = -1;

                        @Override
                        protected void onMethodExit(int opcode) {
                            if (opcode == ARETURN) {
                                if (stackLocal == -1) {
                                    stackLocal = newLocal(Type.getObjectType("net/minecraft/item/ItemStack"));
                                }
                                storeLocal(stackLocal);
                                loadThis();
                                loadArg(0);
                                loadLocal(stackLocal);
                                invokeStatic(Type.getObjectType(HOOKS), new Method("onFurnaceDecrStack", "(Lnet/minecraft/tileentity/TileEntityFurnace;ILnet/minecraft/item/ItemStack;)V"));
                                loadLocal(stackLocal);
                            }
                        }
                    };
                }

                if (("readFromNBT".equals(name) || "func_145839_a".equals(name)) && "(Lnet/minecraft/nbt/NBTTagCompound;)V".equals(desc)) {
                    return new AdviceAdapter(Opcodes.ASM5, mv, access, name, desc) {
                        @Override
                        protected void onMethodExit(int opcode) {
                            if (opcode == RETURN) {
                                loadThis();
                                loadArg(0);
                                invokeStatic(Type.getObjectType(HOOKS), new Method("onReadFromNbt", "(Lnet/minecraft/tileentity/TileEntityFurnace;Lnet/minecraft/nbt/NBTTagCompound;)V"));
                            }
                        }
                    };
                }

                if (("writeToNBT".equals(name) || "func_189515_b".equals(name)) && "(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;".equals(desc)) {
                    return new AdviceAdapter(Opcodes.ASM5, mv, access, name, desc) {
                        private int nbtLocal = -1;

                        @Override
                        protected void onMethodExit(int opcode) {
                            if (opcode == ARETURN) {
                                if (nbtLocal == -1) {
                                    nbtLocal = newLocal(Type.getObjectType("net/minecraft/nbt/NBTTagCompound"));
                                }
                                storeLocal(nbtLocal);
                                loadThis();
                                loadLocal(nbtLocal);
                                invokeStatic(Type.getObjectType(HOOKS), new Method("onWriteToNbt", "(Lnet/minecraft/tileentity/TileEntityFurnace;Lnet/minecraft/nbt/NBTTagCompound;)V"));
                                loadLocal(nbtLocal);
                            }
                        }
                    };
                }

                return mv;
            }
        }, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    private byte[] transformSlotFurnaceOutput(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                if (("decrStackSize".equals(name) || "func_75209_a".equals(name)) && "(I)Lnet/minecraft/item/ItemStack;".equals(desc)) {
                    return new AdviceAdapter(Opcodes.ASM5, mv, access, name, desc) {
                        @Override
                        protected void onMethodEnter() {
                            invokeStatic(Type.getObjectType(HOOKS), new Method("beginPlayerExtract", "()V"));
                        }

                        @Override
                        protected void onMethodExit(int opcode) {
                            invokeStatic(Type.getObjectType(HOOKS), new Method("endPlayerExtract", "()V"));
                        }
                    };
                }

                if (("onCrafting".equals(name) || "func_75208_c".equals(name)) && "(Lnet/minecraft/item/ItemStack;)V".equals(desc)) {
                    return new AdviceAdapter(Opcodes.ASM5, mv, access, name, desc) {
                        @Override
                        protected void onMethodExit(int opcode) {
                            if (opcode == RETURN) {
                                loadThis();
                                visitFieldInsn(GETFIELD, "net/minecraft/inventory/SlotFurnaceOutput", "field_75229_a", "Lnet/minecraft/entity/player/EntityPlayer;");
                                loadThis();
                                visitFieldInsn(GETFIELD, "net/minecraft/inventory/Slot", "field_75224_c", "Lnet/minecraft/inventory/IInventory;");
                                invokeStatic(Type.getObjectType(HOOKS), new Method("onOutputSlotCrafted", "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/IInventory;)V"));
                            }
                        }
                    };
                }

                return mv;
            }
        }, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}
