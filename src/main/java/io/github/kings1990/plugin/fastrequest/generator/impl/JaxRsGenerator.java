package io.github.kings1990.plugin.fastrequest.generator.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CollectionUtils;
import io.github.kings1990.plugin.fastrequest.config.Constant;
import io.github.kings1990.plugin.fastrequest.config.FastRequestComponent;
import io.github.kings1990.plugin.fastrequest.generator.FastUrlGenerator;
import io.github.kings1990.plugin.fastrequest.model.DataMapping;
import io.github.kings1990.plugin.fastrequest.model.FastRequestConfiguration;
import io.github.kings1990.plugin.fastrequest.model.ParamGroup;
import io.github.kings1990.plugin.fastrequest.model.ParamNameType;
import io.github.kings1990.plugin.fastrequest.parse.BodyParamParse;
import io.github.kings1990.plugin.fastrequest.parse.PathValueParamParse;
import io.github.kings1990.plugin.fastrequest.parse.RequestParamParse;
import io.github.kings1990.plugin.fastrequest.view.CommonConfigView;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class JaxRsGenerator extends FastUrlGenerator {
    private FastRequestConfiguration config = FastRequestComponent.getInstance().getState();
    private static final Logger LOGGER = Logger.getInstance(CommonConfigView.class);
    private PathValueParamParse pathValueParamParse = new PathValueParamParse();
    private BodyParamParse bodyParamParse = new BodyParamParse();
    private RequestParamParse requestParamParse = new RequestParamParse();


    @Override
    public String generate(PsiElement psiElement) {

        ParamGroup paramGroup = config.getParamGroup();
        if (!(psiElement instanceof PsiMethod)) {
            return StringUtils.EMPTY;
        }
        PsiMethod psiMethod = (PsiMethod) psiElement;
        String methodUrl = getMethodRequestMappingUrl(psiMethod);
        String classUrl = getClassRequestMappingUrl(psiMethod);
        String originUrl = classUrl + "/" + methodUrl;
        originUrl = originUrl.replace("//", "/");
        paramGroup.setOriginUrl(originUrl);

        List<ParamNameType> methodUrlParamList = getMethodUrlParamList(psiMethod);
        List<ParamNameType> methodBodyParamList = getMethodBodyParamList(psiMethod);

        //pathParam
        LinkedHashMap<String, Object> pathParamMap = pathValueParamParse.parseParam(config, methodUrlParamList);

        //requestParam
        LinkedHashMap<String, Object> requestParamMap = requestParamParse.parseParam(config, methodUrlParamList);

        //bodyParam
        LinkedHashMap<String, Object> bodyParamMap = bodyParamParse.parseParam(config, methodBodyParamList);

        //methodType
        String methodType = getMethodType(psiMethod);

        String methodDescription = getMethodDescription(psiMethod);

        paramGroup.setPathParamMap(pathParamMap);
        paramGroup.setRequestParamMap(requestParamMap);
        paramGroup.setBodyParamMap(bodyParamMap);
        paramGroup.setMethodType(methodType);
        paramGroup.setMethodDescription(methodDescription);

        paramGroup.setClassName(((PsiMethodImpl) psiElement).getContainingClass().getQualifiedName());
        paramGroup.setMethod(psiMethod.getName());
        Module moduleForFile = ModuleUtil.findModuleForPsiElement(psiElement);
        if (moduleForFile != null) {
            String name = moduleForFile.getName();
            paramGroup.setModule(name);
        }
        return null;
    }

    private String getMethodType(PsiMethod psiMethod) {
        Constant.JaxRsMappingMethodConfig[] mappingConfigs = Constant.JaxRsMappingMethodConfig.values();
        for (Constant.JaxRsMappingMethodConfig mappingConfig : mappingConfigs) {
            String code = mappingConfig.getCode();
            String methodType = mappingConfig.getMethodType();
            PsiAnnotation annotation = psiMethod.getAnnotation(code);
            if (annotation != null) {
                return methodType;
            }
        }
        return "GET";
    }

    @Override
    public String getMethodRequestMappingUrl(PsiMethod psiMethod) {
        Constant.JaxRsMappingConfig[] mappingConfigArray = Constant.JaxRsMappingConfig.values();
        PsiAnnotation annotationRequestMapping = null;
        for (Constant.JaxRsMappingConfig mappingConfig : mappingConfigArray) {
            String code = mappingConfig.getCode();
            annotationRequestMapping = psiMethod.getAnnotation(code);
            if (annotationRequestMapping != null) {
                break;
            }
        }
        if (annotationRequestMapping == null) {
            return StringUtils.EMPTY;
        }
        //默认取value,再取path
        PsiAnnotationMemberValue value = annotationRequestMapping.findDeclaredAttributeValue("value");
        if (value == null) {
            return StringUtils.EMPTY;
        }
        return value.getText().replace("\"", "");
    }

    @Override
    public String getClassRequestMappingUrl(PsiMethod psiMethod) {
        String mapping = Constant.JaxRsMappingConfig.PATH.getCode();
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) {
            return StringUtils.EMPTY;
        }
        PsiAnnotation annotationMapping = containingClass.getAnnotation(mapping);
        if (annotationMapping == null) {
            return StringUtils.EMPTY;
        }
        PsiAnnotationMemberValue value = annotationMapping.findDeclaredAttributeValue("value");
        if (value == null) {
            return StringUtils.EMPTY;
        }
        String classUrl = value.getText();
        List<DataMapping> urlReplaceMappingList = config.getUrlReplaceMappingList();
        for (DataMapping dataMapping : urlReplaceMappingList) {
            classUrl = classUrl.replace(dataMapping.getType(), dataMapping.getValue());
        }
        return classUrl.replace("\"", "");
    }

    @Override
    public List<ParamNameType> getMethodUrlParamList(PsiMethod psiMethod) {
        List<ParamNameType> result = new ArrayList<>();
        Constant.JaxRsUrlParamConfig[] urlParamConfigArray = Constant.JaxRsUrlParamConfig.values();
        PsiParameterList parameterList = psiMethod.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        for (PsiParameter param : parameters) {
            boolean parseFlag = false;
            PsiClass psiClass = PsiUtil.resolveClassInType(param.getType());
            for (Constant.JaxRsUrlParamConfig config : urlParamConfigArray) {
                PsiAnnotation annotation = param.getAnnotation(config.getCode());
                if (annotation != null) {
                    //PathVariable  RequestParam
                    Integer parseType = config.getParseType();
                    if (parseType == 1 || parseType == 2) {
                        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
                        String name = value == null ? param.getName() : value.getText().replace("\"", "");
                        ParamNameType paramNameType = new ParamNameType(name, param.getType().getCanonicalText(), psiClass, parseType, param.getType());
                        result.add(paramNameType);
                        parseFlag = true;
                        break;
                    }
                    parseFlag = true;
                }
            }
            if (!parseFlag) {
                PsiAnnotation annotation = psiMethod.getAnnotation("javax.ws.rs.Consumes");
                PsiAnnotationMemberValue value;
                if (annotation != null && (value = annotation.findDeclaredAttributeValue("value")) != null &&
                        (value.getText().contains("application/json") || value.getText().contains("MediaType.APPLICATION_JSON"))) {
                    continue;
                } else {
                    PsiClass containingClass = psiMethod.getContainingClass();
                    if (containingClass != null &&
                            ((annotation = containingClass.getAnnotation("javax.ws.rs.Consumes")) != null
                                    && (value = annotation.findDeclaredAttributeValue("value")) != null
                                    && (value.getText().contains("application/json") || value.getText().contains("MediaType.APPLICATION_JSON")))) {
                        continue;
                    }
                }
                //默认无注解传参 requestParam
                ParamNameType paramNameType = new ParamNameType(param.getName(), param.getType().getCanonicalText(), psiClass, 2, param.getType());
                result.add(paramNameType);
            }
        }
        return result;
    }

    @Override
    public List<ParamNameType> getMethodBodyParamList(PsiMethod psiMethod) {
        List<ParamNameType> result = new ArrayList<>();
        PsiParameterList parameterList = psiMethod.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();
        for (PsiParameter param : parameters) {
            String canonicalText = param.getType().getCanonicalText();
            PsiClass psiClass = null;
            if (CollectionUtils.isCollectionClassOrInterface(param.getType())) {
                PsiClassReferenceType t = (PsiClassReferenceType) PsiUtil.extractIterableTypeParameter(param.getType(), false);
                if (t != null) {
                    psiClass = t.resolve();
                }
            } else {
                psiClass = PsiUtil.resolveClassInType(param.getType());
            }
            if (psiClass == null) {
                continue;
            }
            PsiAnnotation annotation = psiMethod.getAnnotation("javax.ws.rs.Consumes");
            PsiAnnotationMemberValue value;
            if (annotation != null && (value = annotation.findDeclaredAttributeValue("value")) != null
                    && (value.getText().contains("application/json") || value.getText().contains("MediaType.APPLICATION_JSON"))) {
                ParamNameType paramNameType = new ParamNameType(param.getName(), canonicalText, psiClass, 3, param.getType());
                result.add(paramNameType);
            } else {
                PsiClass containingClass = psiMethod.getContainingClass();
                if (containingClass != null &&
                        ((annotation = containingClass.getAnnotation("javax.ws.rs.Consumes")) != null
                                && (value = annotation.findDeclaredAttributeValue("value")) != null
                                && (value.getText().contains("application/json") || value.getText().contains("MediaType.APPLICATION_JSON")))) {
                    ParamNameType paramNameType = new ParamNameType(param.getName(), canonicalText, psiClass, 3, param.getType());
                    result.add(paramNameType);
                }
            }
        }
        return result;
    }

    @Override
    public String getMethodDescription(PsiMethod psiMethod) {
        //javadoc中获取
        PsiDocComment docComment = psiMethod.getDocComment();
        StringBuilder commentStringBuilder = new StringBuilder();
        if (docComment != null) {
            PsiElement[] descriptionElements = docComment.getDescriptionElements();
            for (PsiElement descriptionElement : descriptionElements) {
                if (descriptionElement instanceof PsiDocToken) {
                    commentStringBuilder.append(descriptionElement.getText());
                }
            }
        }
        return commentStringBuilder.toString().trim();
    }
}
