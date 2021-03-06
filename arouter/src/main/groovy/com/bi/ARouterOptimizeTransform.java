package com.bi;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.bi.aroter.ARouterCreate;
import com.bi.util.AndroidJarPath;

import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javassist.ClassPool;


/**
 * <br> ClassName:   ${className}
 * <br> Description:
 * <br>
 * <br> @author:      谢文良
 * <br> Date:        2018/7/25 14:43
 */
public class ARouterOptimizeTransform extends Transform {
    private Project project;

    public ARouterOptimizeTransform(Project project) {
        this.project = project;
    }

    @Override
    public String getName() {
        return "ARouterOptimize";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws IOException {
        System.out.println("ARouterOptimizeTransform_start...");
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        // 删除上次编译目录
        outputProvider.deleteAll();
        try {
            ClassPool mClassPool = new ClassPool(ClassPool.getDefault());
            // 添加android.jar目录
            mClassPool.appendClassPath(AndroidJarPath.getPath(project));
            Map<String, String> dirMap = new HashMap<>();
            Map<String, String> jarMap = new HashMap<>();
            File mARouterOut = null;
            for (TransformInput input : inputs) {
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    // 获取output目录
                    File dest = outputProvider.getContentLocation(directoryInput.getName(),
                            directoryInput.getContentTypes(), directoryInput.getScopes(),
                            Format.DIRECTORY);
                    if (mARouterOut == null) {
                        mARouterOut = outputProvider.getContentLocation(directoryInput.getName(),
                                directoryInput.getContentTypes(), directoryInput.getScopes(),
                                Format.DIRECTORY);
                    }
                    dirMap.put(directoryInput.getFile().getAbsolutePath(), dest.getAbsolutePath());
                    mClassPool.appendClassPath(directoryInput.getFile().getAbsolutePath());
                }

                for (JarInput jarInput : input.getJarInputs()) {
                    // 重命名输出文件
                    String jarName = jarInput.getName();
                    String md5Name = DigestUtils.md5Hex(jarInput.getFile().getAbsolutePath());
                    if (jarName.endsWith(".jar")) {
                        jarName = jarName.substring(0, jarName.length() - 4);
                    }
                    //生成输出路径
                    File dest = outputProvider.getContentLocation(jarName + md5Name,
                            jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);

                    if (mARouterOut == null) {
                        mARouterOut = outputProvider.getContentLocation(jarName + md5Name + 1,
                                jarInput.getContentTypes(), jarInput.getScopes(), Format.DIRECTORY);
                    }
                    jarMap.put(jarInput.getFile().getAbsolutePath(), dest.getAbsolutePath());
                    mClassPool.appendClassPath(new JarClassPath(jarInput.getFile().getAbsolutePath()));
                }
            }

            if (mARouterOut != null) {
                ARouterCreate.getInstance().createClass(mARouterOut, mClassPool);
            }

            for (Map.Entry<String, String> item : dirMap.entrySet()) {
                System.out.println("ARouterOptimize ： perform_directory : " + item.getKey() + "to path :" + item.getValue());
                Inject.injectDir(item.getKey(), item.getValue(), mClassPool);
            }

            for (Map.Entry<String, String> item : jarMap.entrySet()) {
                System.out.println("ARouterOptimize ： perform_jar : " + item.getKey() + "to path :" + item.getValue());
                Inject.injectJar(item.getKey(), item.getValue(), mClassPool);
            }
            if (mARouterOut != null) {
                ARouterCreate.getInstance().writeToFile();
                ARouterCreate.reSet();
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        System.out.println("ARouterOptimizeTransform_end...");
    }
}
