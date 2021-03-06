package org.weceem.services

import org.weceem.AbstractWeceemIntegrationTest

import org.weceem.content.*
import org.weceem.html.WcmHTMLContent
import grails.test.mixin.TestMixin
import grails.test.mixin.integration.IntegrationTestMixin

@TestMixin(IntegrationTestMixin)
class WcmContentDependencyServiceTests extends AbstractWeceemIntegrationTest {

    def statusPublished
    def statusDraft
    
    def spaceA
    def spaceB
    
    def templateA
    def templateB
    def childA1
    def grandchildA1
    def childA2
    def parentA
    def rootNode1
    
    def templateInSpaceB
    def htmlInSpaceB
    
    def wcmContentDependencyService

    void setUp() {
        super.setUp()
                
        WcmContentDependencyService.metaClass.getLog = { ->
            [debugEnabled: true, debug: { s -> println s } ]
        }
        
        // Flush previous test data
        wcmContentDependencyService.reset()

        createContent {
            statusPublished = status(code: 400)
            statusDraft = status(code: 100, description: "draft", publicContent: false)
        }

        spaceA = new WcmSpace(name: 'a', aliasURI: 'a')
        assert spaceA.save(flush: true)

        spaceB = new WcmSpace(name: 'b', aliasURI: 'b')
        assert spaceB.save(flush: true)

        createContent {
            templateA = content(WcmTemplate) {
                title = 'template'
                aliasURI = 'templateA'
                space = spaceA
                status = statusPublished
                content = 'template content'
                contentDependencies = 'parent-a/**'
            }

            templateB = content(WcmTemplate) {
                title = 'template'
                aliasURI = 'templateB'
                space = spaceA
                status = statusPublished
                content = 'template content'
            }

            childA1 = content(WcmHTMLContent) {
                space = spaceA
                status = statusPublished
                title = "Child A1"
                content = "Child A1 content"
            }

            grandchildA1 = content(WcmHTMLContent) {
                space = spaceA
                status = statusPublished
                title = "Grandchild A1"
                content = "Grandchild A1 content"
            }

            childA1.addToChildren(grandchildA1)
            
            childA2 = content(WcmHTMLContent) {
                space = spaceA
                status = statusPublished
                title = "Child A2"
                content = "Child A2 content"
                template = templateB
            }

            parentA = content(WcmHTMLContent) {
                space = spaceA
                aliasURI = 'parent-a'
                status = statusPublished
                title = "Parent A"
                content = "Parent A content"
                template = templateA
            }
            parentA.addToChildren(childA1)
            parentA.addToChildren(childA2)

            content(WcmHTMLContent) {
                status = statusPublished
                space = spaceA
                title = "Parent B"
                content = "Parent B content"
            }

        
            rootNode1 = content(WcmHTMLContent) {
                space = spaceA
                status = statusPublished
                title = "Root node 1"
                content = "Root node 1 content"
            }
        }
        
        // Space B
        createContent {
            templateInSpaceB = content(WcmTemplate) {
                title = 'templateSpaceB'
                aliasURI = 'templateA'
                space = spaceB
                status = statusPublished
                content = 'template content in space B'
                contentDependencies = 'blog/**'
            }

            htmlInSpaceB = content(WcmHTMLContent) {
                status = statusPublished
                space = spaceB
                template = templateInSpaceB
                title = "space b html"
                content = "html in space B content"
            }
        }
        
        wcmContentDependencyService.reload()
    }
    
    void testDependenciesLoadCorrectly() {

        dumpInfo()

        assert (['parent-a/**'].equals(wcmContentDependencyService.getDependencyPathsOf(templateA)))
        assert (['templateA'].equals(wcmContentDependencyService.getDependencyPathsOf(parentA)))
        assert (['templateA'].equals(wcmContentDependencyService.getDependencyPathsOf(childA1)))
        assert (['templateB'].equals(wcmContentDependencyService.getDependencyPathsOf(childA2)))

        dumpInfo()
        
        def templDeps = wcmContentDependencyService.getContentDependentOn(templateA)
        println "TemplateA deps: ${templDeps*.absoluteURI} / ${templDeps*.id}"
        println "Expected TemplateA deps: ${[childA1, grandchildA1, parentA]*.absoluteURI} / ${[childA1, grandchildA1, parentA]*.id}"
        assert ([childA1, grandchildA1, parentA]*.id.sort() == templDeps*.id.sort())

        def child1Deps = wcmContentDependencyService.getContentDependentOn(childA1)
        println "child1Deps deps: ${child1Deps*.absoluteURI} / ${child1Deps*.id}"
        assert ([templateA, parentA, grandchildA1]*.id.sort() == child1Deps*.id.sort())
        assert ([templateA, parentA, grandchildA1, childA1]*.id.sort() == wcmContentDependencyService.getContentDependentOn(childA2)*.id.sort())
        assert ([]*.id.sort() == wcmContentDependencyService.getContentDependentOn(parentA)*.id.sort())

        assert ([parentA, childA1, grandchildA1]*.id.sort() == wcmContentDependencyService.getContentDependentOn(templateA)*.id.sort())

        assert ([parentA, childA1, grandchildA1, templateA, childA2]*.id.sort() == wcmContentDependencyService.getContentDependentOn(templateB)*.id.sort())

        assert 0 == wcmContentDependencyService.getContentDependentOn(parentA).size()
    }
    

    void testGetDependenciesOf() {

        dumpInfo()

        def templDeps = wcmContentDependencyService.getDependenciesOf(templateA)
        println "TemplateA deps: ${templDeps*.absoluteURI}"
        // TemplateA indirectly depends on templateB because childA2 is dependent on changes to templateB
        assert ([childA1, childA2, grandchildA1, templateB]*.id.sort() == templDeps*.id.sort())
        
        assert ([templateA, childA2, grandchildA1, childA1, templateB]*.id.sort() == wcmContentDependencyService.getDependenciesOf(parentA)*.id.sort())
        assert ([templateA, grandchildA1, childA2, templateB]*.id.sort() == wcmContentDependencyService.getDependenciesOf(childA1)*.id.sort())
        assert ([templateB]*.id.sort() == wcmContentDependencyService.getDependenciesOf(childA2)*.id.sort())
    }

    void testGetContentDependentOn() {

        dumpInfo()

        assert ([parentA, childA1, grandchildA1]*.id.sort() == wcmContentDependencyService.getContentDependentOn(templateA)*.id.sort())
        assert ([]*.id.sort() == wcmContentDependencyService.getContentDependentOn(parentA)*.id.sort())
        assert ([templateA, parentA, grandchildA1]*.id.sort() == wcmContentDependencyService.getContentDependentOn(childA1)*.id.sort())
        assert ([templateA, parentA, childA1, grandchildA1]*.id.sort() == wcmContentDependencyService.getContentDependentOn(childA2)*.id.sort())
    }

    void testDependencyInfoDoesNotClashAcrossSpacesForSameURI() {
        assert ([htmlInSpaceB]*.id.sort() == wcmContentDependencyService.getContentDependentOn(templateInSpaceB)*.id.sort())
        assert ([templateInSpaceB]*.id.sort() == wcmContentDependencyService.getDependenciesOf(htmlInSpaceB)*.id.sort())

        assert ([parentA, childA1, grandchildA1]*.id.sort() == wcmContentDependencyService.getContentDependentOn(templateA)*.id.sort())
        assert ([templateA, childA2, grandchildA1, childA1, templateB]*.id.sort() == wcmContentDependencyService.getDependenciesOf(parentA)*.id.sort())
    }
    
    void dumpInfo() {
        wcmContentDependencyService.dumpDependencyInfo(true)
    }
}
