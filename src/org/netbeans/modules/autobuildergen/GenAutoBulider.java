/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.autobuildergen;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.spi.editor.codegen.CodeGenerator;
import org.netbeans.spi.editor.codegen.CodeGeneratorContextProvider;
import org.openide.util.Lookup;

public class GenAutoBulider implements CodeGenerator {
    
    JTextComponent textComp;

    /**
     *
     * @param context containing JTextComponent and possibly other items
     * registered by {@link CodeGeneratorContextProvider}
     */
    private GenAutoBulider( Lookup context ) { // Good practice is not to save Lookup outside ctor
        textComp = context.lookup( JTextComponent.class );
    }
    
    @MimeRegistration(mimeType = "text/x-java", service = CodeGenerator.Factory.class)
    public static class Factory implements CodeGenerator.Factory {
        
        public List<? extends CodeGenerator> create( Lookup context ) {
            return Collections.singletonList( new GenAutoBulider( context ) );
        }
    }

    public String getDisplayName() {
        return "AutoValue.Builder Generate";
    }

    public void invoke() {
        try
        {
            
            Document doc = textComp.getDocument();
            JavaSource javaSource = JavaSource.forDocument( doc );
            CancellableTask task = new CancellableTask<WorkingCopy>() {
                public void run( WorkingCopy workingCopy ) throws IOException {
                    workingCopy.toPhase( Phase.RESOLVED );
                    CompilationUnitTree cut = workingCopy.getCompilationUnit();
                    TreeMaker make = workingCopy.getTreeMaker();
                    
                    cut.getTypeDecls()
                            .stream()
                            .filter( typeDecl -> Tree.Kind.CLASS == typeDecl.getKind() )
                            .map( typeDecl -> (ClassTree) typeDecl ).forEach( new Consumer<ClassTree>() {
                        @Override
                        public void accept( ClassTree clazz ) {
                            List<MethodTree> methodTrees = clazz.getMembers().stream().filter( t -> Tree.Kind.METHOD == t.getKind() ).map( t -> (MethodTree) t ).collect( Collectors.toList() );
                            
                            AnnotationTree annotationTree = make.Annotation( make.Type( "AutoValue.Builder" ), List.of() );
                            List<VariableTree> vTree = new ArrayList<>();
                            List<MethodTree> mt = methodTrees.stream().map( classVariable ->
                            {
                                String variableName = classVariable.getName().toString();
                                VariableTree parameter
                                             = make.Variable( make.Modifiers( Set.of(),
                                                                              Collections.<AnnotationTree>emptyList() ),
                                                              variableName,
                                                              classVariable.getReturnType(),
                                                              null );
                                
                                MethodTree newMethod
                                           = make.Method( classVariable.getModifiers(),
                                                          variableName,
                                                          make.Type( "Builder" ),
                                                          Collections.<TypeParameterTree>emptyList(),
                                                          Collections.singletonList( parameter ),
                                                          Collections.<ExpressionTree>emptyList(),
                                                          classVariable.getBody(),
                                                          null );
                                vTree.add( parameter );
                                return newMethod;
                                
                            }
                            ).collect( Collectors.toList() );

                            //Remove Init From PararmeterTree
                            vTree.remove( 0 );

                            //the build method in Builder class
                            MethodTree lastMethod = make.Method(
                                    make.Modifiers(
                                            Set.of( Modifier.PUBLIC, Modifier.ABSTRACT ),
                                            List.of() ),
                                    "build",
                                    make.Type( clazz.getSimpleName().toString() ),
                                    Collections.<TypeParameterTree>emptyList(),
                                    Collections.<VariableTree>emptyList(),
                                    Collections.<ExpressionTree>emptyList(),
                                    (BlockTree) null,
                                    null );

                            // Generate builder method
                            MethodTree builderMeth = make.Method(
                                    make.Modifiers(
                                            Set.of( Modifier.PUBLIC, Modifier.STATIC ),
                                            List.of() ),
                                    "builder",
                                    make.Type( "Builder" ),
                                    Collections.<TypeParameterTree>emptyList(),
                                    Collections.<VariableTree>emptyList(),
                                    Collections.<ExpressionTree>emptyList(),
                                    String.format( "{return new AutoValue_%s.Builder()}", clazz.getSimpleName().toString() ),
                                    null );

                            //Build create Method for Class
                            MethodTree createMeth = make.Method(
                                    make.Modifiers(
                                            Set.of( Modifier.PUBLIC, Modifier.STATIC ),
                                            List.of() ),
                                    "create",
                                    make.Type( clazz.getSimpleName().toString() ),
                                    Collections.<TypeParameterTree>emptyList(),
                                    vTree,
                                    Collections.<ExpressionTree>emptyList(),
                                    "{ return builder().build(); }",
                                    null );

                            //Builder Class Creation
                            ClassTree clazzBuilder = make.Class(
                                    make.Modifiers( Set.of( Modifier.PUBLIC, Modifier.ABSTRACT, Modifier.STATIC ),
                                                    List.of( annotationTree ) ),
                                    "Builder",
                                    Collections.<TypeParameterTree>emptyList(),
                                    null,
                                    Collections.<ExpressionTree>emptyList(),
                                    mt );
                            
                            ClassTree updateLastMethod = make.addClassMember( clazzBuilder, lastMethod );
                            ClassTree updateWithCreateMeth = make.addClassMember( clazz, createMeth );
                            ClassTree updateClass = make.addClassMember( updateWithCreateMeth, builderMeth );
                            ClassTree finalUpdate = make.addClassMember( updateClass, updateLastMethod );
                            workingCopy.rewrite( clazz, finalUpdate );
                        }
                    } );
                }
                
                public void cancel() {
                }
            };
            ModificationResult result = javaSource.runModificationTask( task );
            result.commit();
            
        } catch (IOException | IllegalArgumentException ex)
        {
            ex.printStackTrace();
        }
    }
    
}
