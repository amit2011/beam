package beam.agentsim.agents.choice.logit;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Random;

public class MultinomialLogit implements AbstractLogit, Cloneable {
    NestedLogit tree = null; // We use the more general NL to represent an MNL and with an assumed single top-level nest

    public MultinomialLogit(NestedLogit theTree) {
        this.tree = theTree;
    }

    public static MultinomialLogit multinomialLogitFactory(String multinomialLogitTreeAsXML) {
        SAXBuilder saxBuilder = new SAXBuilder();
        InputStream stream = new ByteArrayInputStream(multinomialLogitTreeAsXML.getBytes(StandardCharsets.UTF_8));
        Document document;
        try {
            document = saxBuilder.build(stream);
            return MultinomialLogit.multinomialLogitFactory(document.getRootElement());
        } catch (JDOMException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static MultinomialLogit multinomialLogitFactory(Element rootElem) {
        NestedLogitData theData = new NestedLogitData();
        theData.setNestName(rootElem.getAttributeValue("name"));
        NestedLogit tree = new NestedLogit(theData);
        UtilityFunction utility;
        for (int i = 0; i < rootElem.getChildren().size(); i++) {
            Element elem = (Element) rootElem.getChildren().get(i);
            if (elem.getName().equalsIgnoreCase("elasticity")) {
                theData.setElasticity(Double.parseDouble(elem.getValue()));
            } else if (elem.getName().equalsIgnoreCase("alternative")) {
                if (tree.children == null) {
                    tree.children = new LinkedList<NestedLogit>();
                }
                NestedLogit child = NestedLogit.NestedLogitFactory(elem);
                child.parent = tree;
                tree.children.add(child);
            } else {
                throw new org.jdom.IllegalDataException("Unexpected xml element type " + elem.getName());
            }
        }
        return new MultinomialLogit(tree);
    }

    @Override
    public DiscreteProbabilityDistribution evaluateProbabilities(LinkedHashMap<String, LinkedHashMap<String, Double>> inputData) {
        return tree.evaluateProbabilities(inputData);
    }

    @Override
    public String makeRandomChoice(LinkedHashMap<String, LinkedHashMap<String, Double>> inputData, Random rand) {
        return tree.makeRandomChoice(inputData, rand);
    }

    @Override
    public Double getExpectedMaximumUtility() {
        return tree.getExpectedMaximumUtility();
    }

    @Override
    public MultinomialLogit clone() {
        MultinomialLogit mnl = new MultinomialLogit(this.tree);
        mnl.tree = new NestedLogit(this.tree);
        return mnl;
    }

    @Override
    public void clear() {
        tree.clear();
    }
}
