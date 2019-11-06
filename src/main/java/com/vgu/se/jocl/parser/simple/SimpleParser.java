/**************************************************************************
Copyright 2019 Vietnamese-German-University

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

@author: thian
***************************************************************************/

package com.vgu.se.jocl.parser.simple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;

import com.vgu.se.jocl.exception.OclParserException;
import com.vgu.se.jocl.expressions.AssociationClassCallExp;
import com.vgu.se.jocl.expressions.BooleanLiteralExp;
import com.vgu.se.jocl.expressions.IntegerLiteralExp;
import com.vgu.se.jocl.expressions.IteratorExp;
import com.vgu.se.jocl.expressions.IteratorKind;
import com.vgu.se.jocl.expressions.LiteralExp;
import com.vgu.se.jocl.expressions.NullLiteralExp;
import com.vgu.se.jocl.expressions.OclExp;
import com.vgu.se.jocl.expressions.Operation;
import com.vgu.se.jocl.expressions.OperationCallExp;
import com.vgu.se.jocl.expressions.PropertyCallExp;
import com.vgu.se.jocl.expressions.RealLiteralExp;
import com.vgu.se.jocl.expressions.StringLiteralExp;
import com.vgu.se.jocl.expressions.TypeExp;
import com.vgu.se.jocl.expressions.Variable;
import com.vgu.se.jocl.expressions.VariableExp;
import com.vgu.se.jocl.parser.interfaces.Parser;
import com.vgu.se.jocl.types.Type;
import com.vgu.se.jocl.utils.UMLContextUtils;

public class SimpleParser implements Parser {

    private List<String> stringArray = new ArrayList<String>();
    private List<String> parenthesisArray = new ArrayList<String>();
    private Stack<Variable> variableStack = new Stack<Variable>();
    private Set<Variable> adhocContextualSet = new HashSet<>();

    private JSONArray ctx;

    private void houseCleanup() {
        this.stringArray.clear();
        this.parenthesisArray.clear();
        this.variableStack.clear();
    }

    @Override
    public void putAdhocContextualSet(Variable v) {
        this.adhocContextualSet.remove(v);
        this.adhocContextualSet.add(v);
    }

    /**
     * This parse an OCL Expression string to OclExp Java Object given a
     * 
     * UML Context file.
     * 
     * @param ctx
     * @param oclExpStr
     * @return OclExp
     */
    @Override
    public OclExp parse(String ocl, JSONArray ctx) {
        houseCleanup();
        this.ctx = ctx;

        String encOcl = encode(ocl);

        return parseOclExp(encOcl, ctx);
    }

    private OclExp parseOclExp(String ocl, JSONArray ctx) {

        if (Pattern.matches("\\(\\d+\\)", ocl)) {
            ocl = decode(ocl).replaceAll("^\\((.*)\\)$", "$1");
        }
        ;

        OclExp oclExp = parseCallExp(ocl, ctx);

        if (oclExp != null) {
            oclExp.setOclStr(decode(ocl));
            return oclExp;
        }

        OclExp litExp = parseLiteralExp(ocl, ctx);
        litExp.setOclStr(decode(ocl));

        return litExp;
    }

    private OclExp parseCallExp(String ocl, JSONArray ctx) {

//        Implementing the operators patterns from lowest precedence
        Pattern[] patterns = { ParserPatterns.IMPLIES_OP_PATT,
                ParserPatterns.XOR_OP_PATT, ParserPatterns.OR_OP_PATT,
                ParserPatterns.AND_OP_PATT,
                ParserPatterns.EQUALITY_OP_PATT,
                ParserPatterns.COMPARISON_OP_PATT,
//                ParserPatterns.ADD_OR_SUBTRACT_OP_PATT,
//                ParserPatterns.MULTIPLY_OR_DIV_OP_PATT,
//                ParserPatterns.NOT_OR_NEGATIVE_OP_PATT,
                ParserPatterns.NOT_OP_PATT };

        Matcher m;
        for (Pattern p : patterns) {
            m = p.matcher(ocl);
            if (m.find()) {
                return parseOperationCallExp(m, ocl, ctx);
            }
        }

//        Check the DOT or ARROW pattern
        m = ParserPatterns.DOT_OR_ARROW_OP_PATT.matcher(ocl);
        if (m.find()) {
            return parseCallExp(m, ocl, ctx);
        }

        return null;
    }

    private OclExp parseOperationCallExp(Matcher m, String ocl,
            JSONArray ctx) {

        String source = trim(m.group(1));
        String operator = trim(m.group(2));
        String body = trim(m.group(3));

        OclExp sourceOcl = parseOclExp(source, ctx);
        OclExp bodyOcl = parseOclExp(body, ctx);
        Type type = getOperationExpType(operator, sourceOcl, bodyOcl);

        OperationCallExp opCallExp = new OperationCallExp(sourceOcl,
                new Operation(operator), bodyOcl);
        opCallExp.setType(type);

        return opCallExp;
    }

    private OclExp parseCallExp(Matcher m, String ocl, JSONArray ctx) {

        String operator = m.group(2);

        switch (operator) {
        case ".":
            return parseDotCase(m, ocl, ctx);
        default: // "->"
            return parseArrowCase(m, ocl, ctx);
        }
    }

    private OclExp parseDotCase(Matcher m, String ocl, JSONArray ctx) {

        String left = trim(m.group(1));
        String right = trim(m.group(3));
        String operation = right.replaceFirst("(\\w*)\\(\\d+\\)", "$1");
        String operationPatt = "(\\w*)(\\((\\d+)\\))";
        Matcher mRight = Pattern.compile(operationPatt).matcher(right);
        Type type = new Type();

        if (mRight.find()) {
//            Used for operation defined in Classifier with paramenters
            OclExp leftExp = parseOclExp(left, ctx);

            String[] arguments = this.parenthesisArray
                    .get(Integer.valueOf(trim(mRight.group(3))))
                    .split(",");

            if (arguments.length == 1 & "".equals(arguments[0])) {
                arguments = new String[0];
            }

            OclExp[] argumentExps = new OclExp[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
//                argumentExps[i] = parse(arguments[i], ctx);
                argumentExps[i] = parseOclExp(arguments[i], ctx);
            }

            type = getOperationExpType(operation, leftExp,
                    argumentExps);

            OperationCallExp opCallExp = new OperationCallExp(leftExp,
                    new Operation(operation), argumentExps);
            opCallExp.setType(type);

            return opCallExp;

        } else {
            OclExp src = parseOclExp(left, ctx);
            String srcType = src.getType().getReferredType();

            if ("Unknown".equals(srcType)) {
                throw new OclParserException(
                        "Cannot parse " + srcType + " type");
            }

            OclExp dotOpCall = null;

            if (UMLContextUtils.isPropertyOfClass(ctx, srcType,
                    right)) {
                dotOpCall = new PropertyCallExp(src, right);
                type = new Type(UMLContextUtils.getAttributeType(ctx,
                        srcType, right));
                dotOpCall.setType(type);

            } else if (UMLContextUtils.isAssociationEndOfClass(ctx,
                    srcType, right)) {
                dotOpCall = new AssociationClassCallExp(src,
                        right);

                String assocName = UMLContextUtils.getAssociation(ctx,
                        srcType, right);

                ((AssociationClassCallExp) dotOpCall)
                        .setAssociation(assocName);

                ((AssociationClassCallExp) dotOpCall)
                        .setOppositeAssociationEnd(UMLContextUtils
                                .getOppositeAssociationEnd(ctx,
                                        assocName, right));

                String opposClassName = UMLContextUtils
                        .getAssociationOppositeClassName(ctx, assocName,
                                srcType);

                ((AssociationClassCallExp) dotOpCall)
                        .setOppositeAssociationEndType(new Type(
                                "Col(" + opposClassName + ")"));

                type = new Type("Col(" + opposClassName + ")");
                dotOpCall.setType(type);

            } else {
                throw new OclParserException(
                        "Cannot parse " + srcType);
            }

            return dotOpCall;
        }
    }

    private OclExp parseArrowCase(Matcher m, String ocl,
            JSONArray ctx) {

        String source = trim(m.group(1));
        String body = trim(replace(m.group(3)));
        String kind = trim(
                m.group(3).replaceFirst("(\\w*)\\(\\d+\\)", "$1"));

        if (IteratorKind.valueOf(kind) == null) {
            throw new OclParserException("Invalid iterator kind!");
        }

//        System.out.println("\n\n" + "\nComplete: " + m.group()
//                + "\nSource: " + source + "\nbody: " + body + "\nkind: "
//                + kind + "\n\n");

        OclExp sourceExp = parseOclExp(source, ctx);

        String iterator = "iterator";
        String iteratorDeclRx = "^(.*)\\|(.*)$";
        Variable variable = new Variable(iterator, new Type());

        if (body.matches(iteratorDeclRx)) {
            iterator = trim(body.replaceFirst(iteratorDeclRx, "$1"));

            String sourceType = sourceExp.getType().getReferredType()
                    .replaceAll("Col\\((\\w+)\\)", "$1");

            Type varType = new Type(sourceType);
            variable = new Variable(sourceExp, iterator, varType);

            if (this.variableStack.contains(variable)) {
                throw new OclParserException(
                        iterator + " already existed!");
            }

            if (this.adhocContextualSet.contains(variable)) {
                throw new OclParserException(
                        iterator + " is defined as free variable!");
            }

            body = trim(body.replaceFirst(iteratorDeclRx, "$2"));

            this.variableStack.push(variable);
//            System.out.println("String : " + body + " --> Stack : "
//                    + variableStack);
        }

        OclExp bodyExp = "".equals(body) ? null
                : parseOclExp(body, ctx);

        if (!iterator.equals("iterator")) {
            this.variableStack.pop();
        }

        OclExp iteratorExp = new IteratorExp(sourceExp, kind, variable,
                bodyExp);
        Type type = getIteratorExpType(sourceExp, kind, bodyExp);
        iteratorExp.setType(type);

        return iteratorExp;
    }

    private OclExp parseLiteralExp(String input, JSONArray ctx) {
        /**
         * Any character between two single quote '' E.g.: "'Lorem ipsum
         * άλφα 123|, !@#$%^&*('"
         * 
         */
        final String STRING_LITERAL_STR = "\\s*\\w*\\{(\\d+)*\\}";
        /**
         * Start ONLY with a word true OR false WITHOUT quote and
         * case-insensitive (?i) <- inline flag E.g.:"true", "false",
         * "True", "False", "TRUE", "FALSE" Note : there is NO single
         * quote
         */
        final String BOOLEAN_LITERAL_STR = "(?i)(^\\btrue\\b|\\bfalse\\b$)";
        /**
         * String contains ONLY digit WITH an optional minus sign (-)
         * denoting negativity; this patterns allows, too, the decimal
         * number by adding optional group (.\\d+)?. This also allows
         * JAVA legal way of writing number using underscore (_) E.g.:
         * "12334", "-12345", "123_3445", "12_234.4893"
         */
        final String NUMERIC_LITERAL_STR = "^-?(\\d+(_\\d+)*(.\\d+)?)(?<!_)$";
        /**
         * This is strictly a natural number. If this string is given a
         * number that is greater than what an Integer can hold, the
         * parser will throw an Error.
         */
        final String INTEGER_LITERAL_STR = "^-?\\d+(_\\d+)*(?<!_)$";
        /**
         * This is strictly a real number. If this string is given a
         * number that is greater than what a Double can hold, the
         * parser will throw an Error.
         */
        final String REAL_LITERAL_STR = "^-?(\\d+(_\\d+)*.\\d+)(?<!_)$";

        if (input.matches(NUMERIC_LITERAL_STR)) {

            if (input.matches(INTEGER_LITERAL_STR)) {

                IntegerLiteralExp intLitExp = new IntegerLiteralExp(
                        Integer.valueOf(input));
                intLitExp.setType(new Type("Integer"));
                return intLitExp;
            } else if (input.matches(REAL_LITERAL_STR)) {

                RealLiteralExp realLitExp = new RealLiteralExp(
                        Double.valueOf(input));
                realLitExp.setType(new Type("Real"));
                return realLitExp;
            }

        } else if (input.matches(STRING_LITERAL_STR)) {
            input = input.replaceFirst(STRING_LITERAL_STR, "$1");
            input = this.stringArray.get(Integer.valueOf(input));

            StringLiteralExp strLitExp = new StringLiteralExp(input);
            strLitExp.setType(new Type("String"));

            return strLitExp;

        } else if (input.matches(BOOLEAN_LITERAL_STR)) {

            BooleanLiteralExp boolLitExp = new BooleanLiteralExp(
                    Boolean.valueOf(input));
            boolLitExp.setType(new Type("Boolean"));

            return boolLitExp;

        } else if (input.length() > 0) {

            // Check if it's a class
            if (UMLContextUtils.isClass(ctx, input)) {
                TypeExp type = new TypeExp(input);
                type.setType(new Type(input));

                return type;
            } else {
                for (int i = 0; i < this.variableStack.size(); i++) {
                    if (this.variableStack.get(i).getName()
                            .equals(input)) {
                        return new VariableExp(
                                this.variableStack.get(i));
                    }
                }

                for (Variable v : this.adhocContextualSet) {
                    if (v.getName().equals(input)) {
                        return new VariableExp(v);
                    }
                }
            }

        } else {
            throw new OclParserException(input + "\n======\n"
                    + "Invalid OCL Literal Expression!");
        }

        return new NullLiteralExp();
    }

    private String encode(String ocl) {

        String encOcl = extractString(ocl);
        encOcl = extractParenthesis(encOcl);

        return encOcl;
    }

    private String decode(String encOcl) {
        String decOcl = String.copyValueOf(encOcl.toCharArray());

        Pattern p = Pattern.compile("((.*)\\()(\\d+)(\\)(.*))");
        Pattern s = Pattern.compile("(.*)(\\{(\\d+)\\})(.*)");

        Matcher mP = p.matcher(decOcl);
        while (mP.find()) {
            String content = this.parenthesisArray
                    .get(Integer.parseInt(mP.group(3)));
            decOcl = decOcl.replaceFirst(p.pattern(),
                    "$1" + content + "$4");
            mP = p.matcher(decOcl);
        }

        Matcher mS = s.matcher(decOcl);
        while (mS.find()) {
            String content = this.stringArray
                    .get(Integer.parseInt(mS.group(3)));
            decOcl = decOcl.replaceFirst(s.pattern(),
                    "$1" + "'" + content + "'" + "$4");
            mS = s.matcher(decOcl);
        }

        return decOcl;
    }

    private String extractString(String encOcl) {

        Pattern pattern = Pattern
                .compile(ParserPatterns.STRING_LITERAL_STR);
        Matcher m = pattern.matcher(encOcl);
        String openBracket = "{";
        String closeBracket = "}";
        int level = 0;

        while (m.find()) {
            String content = m.group(1);
            content = content.replaceAll("^'|'$", "");
            this.stringArray.add(trim(content));
            encOcl = m.replaceFirst(openBracket + level + closeBracket);
            m = pattern.matcher(encOcl);
            level++;
        }

        return encOcl;
    }

    private String extractParenthesis(String encOcl) {

        Pattern pattern = Pattern
                .compile(ParserPatterns.PARENTHESIS_LITERAL_STR);
        Matcher m = pattern.matcher(encOcl);
        String openBracket = "[";
        String closeBracket = "]";
        int level = 0;

        while (m.find()) {
            String content = m.group(1);
            content = content.replaceAll("^\\(|\\)$", "");

            // **********************************************
            // in case of `... operation (a+b)...` being parsed to
            // `... operation [1] ...`
            content = content.replaceAll("\\[", "(");
            content = content.replaceAll("\\]", ")");
            // **********************************************

            this.parenthesisArray.add(trim(content));
            encOcl = m.replaceFirst(openBracket + level + closeBracket);
            m = pattern.matcher(encOcl);
            level++;
        }

        encOcl = encOcl.replaceAll("\\[", "(");
        encOcl = encOcl.replaceAll("\\]", ")");

        return encOcl;
    }

    private String replace(String input) {

        String output = String.copyValueOf(input.toCharArray());
        Pattern p = Pattern.compile("((\\w+)\\()(\\d+)(\\)(.*))");
        Matcher m = p.matcher(output);

        if (m.find()) {
            String content = this.parenthesisArray
                    .get(Integer.valueOf(m.group(3)));

            if ("".equals(content)) {
                return content;
            }

            output = output.replaceFirst(p.pattern(), content);
        }

        return output;
    }

    private String trim(String input) {

        String spacePatt = "^(\\s)*|(\\s)*$";
        Matcher m = Pattern.compile(spacePatt).matcher(input);
        if (m.find()) {
            return input.replaceAll(spacePatt, "");
        }

        return input;
    }

    private Type getOperationExpType(String operationName,
            OclExp leftExp, OclExp... exps) {

        String leftExpType = leftExp.getType().getReferredType();

        Type opType = new Type();
        switch (operationName) {
        case "allInstances":
            opType = new Type("Col(" + leftExpType + ")");
            return opType;
        case "not":
        case "=":
        case "<>":
        case "<":
        case ">":
        case ">=":
        case "<=":
        case "and":
        case "or":
        case "oclIsUndefined":
        case "oclIsKindOf":
        case "oclIsTypeOf":
            opType = new Type("Boolean");
            return opType;
        case "oclAsType":
            String argType = exps[0].getType().getReferredType();
            if (!UMLContextUtils.isSuperClassOf(this.ctx, leftExpType,
                    argType)) {
                throw new OclParserException("\n======\n"
                        + "Cannot perform casting!");
            }

            opType = new Type(argType);
            return opType;
        default:
            return opType;
        }
    }

    private Type getIteratorExpType(OclExp source, String kind,
            OclExp body) {
        Type type = new Type("Invalid");

        switch (IteratorKind.valueOf(kind)) {
        case any:
        case at:
        case first:
        case last:
        case sum:
            return new Type(source.getType().getReferredType()
                    .replaceFirst("^Col\\((\\w+)\\)$", "$1"));

        case asBag:
        case asOrderedSet:
        case asSequence:
        case asSet:
        case excluding:
        case flatten:
        case including:
        case reject:
        case select:
        case sortedBy:
        case union:
            return new Type(source.getType().getReferredType());

        case excludes:
        case excludesAll:
        case exists:
        case forAll:
        case includes:
        case includesAll:
        case isEmpty:
        case isUnique:
        case notEmpty:
            return new Type("Boolean");

        case count:
        case indexOf:
        case one:
        case size:
            return new Type("Integer");
        case collect:
            return new Type(body.getType().getReferredType()
                    .replaceAll("Col\\((\\w+)\\)", "$1"));
        default:
            return type;
        }
    }
}
