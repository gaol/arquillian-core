/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.testenricher.resource;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.Resource;
import javax.naming.InitialContext;

import org.jboss.arquillian.spi.Context;
import org.jboss.arquillian.spi.TestEnricher;

/**
 * Enricher that provide @Resource class and method argument injection.
 *
 * @author <a href="mailto:aknutsen@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public class ResourceInjectionEnricher implements TestEnricher
{
   private static final String RESOURCE_LOOKUP_PREFIX = "java:/comp/env";
   private static final String ANNOTATION_NAME = "javax.annotation.Resource";
   
   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.TestEnricher#enrich(org.jboss.arquillian.spi.Context, java.lang.Object)
    */
   public void enrich(Context context, Object testCase)
   {
      if(SecurityActions.isClassPresent(ANNOTATION_NAME)) 
      {
         injectClass(testCase);
      }
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.TestEnricher#resolve(org.jboss.arquillian.spi.Context, java.lang.reflect.Method)
    */
   public Object[] resolve(Context context, Method method) 
   {
     return new Object[method.getParameterTypes().length];
   }

   protected void injectClass(Object testCase) 
   {
      try 
      {
         @SuppressWarnings("unchecked")
         Class<? extends Annotation> resourceAnnotation = (Class<? extends Annotation>)SecurityActions.getThreadContextClassLoader().loadClass(ANNOTATION_NAME);
         
         List<Field> annotatedFields = SecurityActions.getFieldsWithAnnotation(
               testCase.getClass(), 
               resourceAnnotation);
         
         for(Field field : annotatedFields) 
         {
            Object ejb = lookup(getResourceName(field));
            field.set(testCase, ejb);
         }
         
         List<Method> methods = SecurityActions.getMethodsWithAnnotation(
               testCase.getClass(), 
               resourceAnnotation);
         
         for(Method method : methods) 
         {
            if(method.getParameterTypes().length != 1) 
            {
               throw new RuntimeException("@Resource only allowed on single argument methods");
            }
            if(!method.getName().startsWith("set")) 
            {
               throw new RuntimeException("@Resource only allowed on 'set' methods");
            }
            Object resource = lookup(getResourceName(method.getAnnotation(Resource.class)));
            method.invoke(testCase, resource);
         }
         
      } 
      catch (Exception e) 
      {
         throw new RuntimeException("Could not inject members", e);
      }
   }

   protected Object lookup(String jndiName) throws Exception 
   {
      // TODO: figure out test context ? 
      InitialContext context = new InitialContext();
      return context.lookup(jndiName);
   }
   
   protected String getResourceName(Field field)
   {
      Resource resource = field.getAnnotation(Resource.class);
      String resourceName = getResourceName(resource);
      if(resourceName != null) 
      {
       return resourceName;
      }
      String propertyName = field.getName();
      String className = field.getDeclaringClass().getName();
      return RESOURCE_LOOKUP_PREFIX + "/" + className + "/" + propertyName;
   }
   
   protected String getResourceName(Resource resource)
   {
      String mappedName = resource.mappedName();
      if (!mappedName.equals(""))
      {
         return mappedName;
      }
      String name = resource.name();
      if (!name.equals(""))
      {
         return RESOURCE_LOOKUP_PREFIX + "/" + name;
      }
      return null;
   }
}