package org.hisp.dhis.expression;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

public interface Nodes {

    abstract class AbstractNode<T> implements Node<T> {

        final NodeType type;
        final String rawValue;
        final T value;

        AbstractNode(NodeType type, String rawValue, Function<String, T> converter) {
            this(type, rawValue, converter, (val, ex) -> ex);
        }

        AbstractNode(NodeType type, String rawValue, Function<String, T> converter, BiFunction<String,RuntimeException,RuntimeException> rethrowAs) {
            this.type = type;
            this.rawValue = rawValue;
            try {
                this.value = converter.apply(rawValue);
            } catch (RuntimeException ex) {
                throw rethrowAs.apply(rawValue,ex);
            }
        }

        static <E extends Enum<E>> BiFunction<String,RuntimeException,RuntimeException> rethrowAs(Class<E> valueType, Function<E,String> toText) {
            return (rawValue, ex) -> new IllegalArgumentException(format("Not a valid option for type %s: %s%navailable options are: %s",
                    valueType.getSimpleName(), rawValue, Stream.of(valueType.getEnumConstants()).map(toText).collect(toList())));
        }

        @Override
        public final NodeType getType() {
            return type;
        }

        @Override
        public final String getRawValue() {
            return rawValue;
        }

        @Override
        public final T getValue()
        {
            return value;
        }

        @Override
        public void visit(Consumer<Node<?>> visitor, Predicate<Node<?>> filter) {
            if (filter.test(this)) {
                visitor.accept(this);
            }
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            toString(str, "");
            return str.toString();
        }

        void toString(StringBuilder str, String indent) {
            str.append(indent).append(getClass().getSimpleName())
                    .append('[').append(getType().name()).append(" ").append(getRawValue()).append("]\n");
        }
    }

    abstract class ComplexNode<T> extends AbstractNode<T> {

        private List<Node<?>> children = new ArrayList<>();

        ComplexNode(NodeType type, String rawValue, Function<String, T> converter) {
            super(type, rawValue, converter);
        }

        ComplexNode(NodeType type, String rawValue, Function<String, T> converter, BiFunction<String, RuntimeException, RuntimeException> rethrowAs) {
            super(type, rawValue, converter, rethrowAs);
        }

        @Override
        public final int size() {
            return children.size();
        }

        @Override
        public final Node<?> child(int index) {
            return children.get(index);
        }

        @Override
        public final Node<?> child(int n, NodeType ofType) {
            int i = 0;
            while (n >= 0 && i < children.size()) {
                if (children.get(i++).getType() == ofType) {
                    n--;
                }
            }
            return children.get(i);
        }

        @Override
        public final void addChild(Node<?> child) {
            children.add(child);
        }

        @Override
        public final void transform(java.util.function.UnaryOperator<List<Node<?>>> transformer) {
            children = transformer.apply(children);
            children.forEach(c -> c.transform(transformer));
        }

        @Override
        public final void visit(Consumer<Node<?>> visitor, Predicate<Node<?>> filter) {
            super.visit(visitor, filter);
            children.forEach(child -> child.visit(visitor, filter));
        }

        @Override
        final void toString(StringBuilder str, String indent) {
            super.toString(str, indent);
            for (Node<?> c : children)
                ((AbstractNode<?>)c).toString(str, indent+"  ");
        }
    }

    final class ComplexTextNode extends ComplexNode<String> {

        public ComplexTextNode(NodeType type, String rawValue) {
            super(type, rawValue, Function.identity());
        }
    }

    final class ArgumentNode extends ComplexNode<Integer> {

        public ArgumentNode(NodeType type, String rawValue) {
            super(type, rawValue, Integer::valueOf);
        }
    }

    final class MethodNode extends ComplexNode<NamedMethod> {

        public MethodNode(NodeType type, String rawValue) {
            super(type, rawValue, NamedMethod::valueOf);
        }
    }

    final class DataValueNode extends ComplexNode<DataValue> {

        public DataValueNode(NodeType type, String rawValue) {
            super(type, rawValue, DataValue::fromSymbol, rethrowAs(DataValue.class, DataValue::getSymbol));
        }
    }

    abstract class SimpleNode<T> extends AbstractNode<T> {

        SimpleNode(NodeType type, String rawValue, Function<String, T> converter) {
            super(type, rawValue, converter);
        }

        SimpleNode(NodeType type, String rawValue, Function<String, T> converter, BiFunction<String, RuntimeException, RuntimeException> rethrowAs) {
            super(type, rawValue, converter, rethrowAs);
        }

    }

    class SimpleTextNode extends SimpleNode<String> {

        public SimpleTextNode(NodeType type, String rawValue) {
            super(type, rawValue, Function.identity());
        }
    }

    final class UnaryOperatorNode extends ComplexNode<UnaryOperator> {

        public UnaryOperatorNode(NodeType type, String rawValue) {
            super(type, rawValue, UnaryOperator::fromSymbol, rethrowAs(UnaryOperator.class, UnaryOperator::getSymbol));
        }
    }

    final class BinaryOperatorNode extends ComplexNode<BinaryOperator> {

        public BinaryOperatorNode(NodeType type, String rawValue) {
            super(type, rawValue, BinaryOperator::fromSymbol, rethrowAs(BinaryOperator.class, BinaryOperator::getSymbol));
        }
    }

    final class BooleanNode extends SimpleNode<Boolean> {

        public  BooleanNode(NodeType type, String rawValue) {
            super(type, rawValue, Boolean::valueOf);
        }
    }

    final class NumberNode extends SimpleNode<Double> {

        public  NumberNode(NodeType type, String rawValue) {
            super(type, rawValue, Double::valueOf);
        }
    }

    final class IntegerNode extends SimpleNode<Integer> {

        public IntegerNode(NodeType type, String rawValue) {
            super(type, rawValue, Integer::valueOf);
        }
    }

    final class DateNode extends SimpleNode<LocalDateTime> {

        public DateNode(NodeType type, String rawValue) {
            super(type, rawValue, LocalDateTime::parse);
        }
    }

    final class ConstantNode extends SimpleNode<Void> {

        public ConstantNode(NodeType type, String rawValue) {
            super(type, rawValue, str -> null);
        }
    }

    final class ReportingRateTypeNode extends SimpleNode<ReportingRateType> {

        public ReportingRateTypeNode(NodeType type, String rawValue) {
            super(type, rawValue, ReportingRateType::valueOf, rethrowAs(ReportingRateType.class, ReportingRateType::name));
        }
    }

    final class ProgramVariableNode extends SimpleNode<ProgramVariable> {

        public ProgramVariableNode(NodeType type, String rawValue) {
            super(type, rawValue, ProgramVariable::valueOf, rethrowAs(ProgramVariable.class, ProgramVariable::name));
        }
    }

    final class NamedValueNode extends SimpleNode<NamedValue>
    {
        public NamedValueNode(NodeType type, String rawValue) {
            super(type, rawValue, NamedValue::valueOf, rethrowAs(NamedValue.class, NamedValue::name));
        }
    }

    final class TagNode extends SimpleNode<Tag> {
        public TagNode(NodeType type, String rawValue) {
            super(type, rawValue, Tag::valueOf, rethrowAs(Tag.class, Tag::name));
        }
    }
}
