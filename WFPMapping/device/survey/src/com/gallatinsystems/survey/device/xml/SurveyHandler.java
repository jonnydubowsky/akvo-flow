package com.gallatinsystems.survey.device.xml;

import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.gallatinsystems.survey.device.domain.AltText;
import com.gallatinsystems.survey.device.domain.Dependency;
import com.gallatinsystems.survey.device.domain.Option;
import com.gallatinsystems.survey.device.domain.Question;
import com.gallatinsystems.survey.device.domain.QuestionGroup;
import com.gallatinsystems.survey.device.domain.Survey;
import com.gallatinsystems.survey.device.domain.ValidationRule;

/**
 * Handler for sax-based xml parser for Survey files
 * 
 * @author Christopher Fagiani
 **/
public class SurveyHandler extends DefaultHandler {

	private static final String QUESTION_GROUP = "questionGroup";
	private static final String HEADING = "heading";
	private static final String QUESTION = "question";
	private static final String ORDER = "order";
	private static final String MANDATORY = "mandatory";
	private static final String TYPE = "type";
	private static final String ID = "id";
	private static final String DEPENDENCY = "dependency";
	private static final String ANSWER = "answer-value";
	private static final String TEXT = "text";
	private static final String OPTION = "option";
	private static final String VALUE = "value";
	private static final String OPTIONS = "options";
	private static final String ALLOW_OTHER = "allowOther";
	private static final String TIP = "tip";
	private static final String VALIDATION_TYPE = "validationType";
	private static final String VALIDATION_RULE = "validationRule";
	private static final String MAX_LENGTH = "maxLength";
	private static final String ALLOW_DEC = "allowDecimal";
	private static final String ALLOW_SIGN = "signed";
	private static final String RENDER_TYPE = "renderType";
	private static final String VIDEO = "video";
	private static final String IMAGE = "image";
	private static final String CAPTION = "caption";
	private static final String SRC = "src";
	private static final String ALLOW_MULT = "allowMultiple";
	private static final String MIN_VAL = "minVal";
	private static final String MAX_VAL = "maxVal";
	private static final String ALT_TEXT = "altText";
	private static final String LANG = "language";
	private static final String LOCKED = "locked";
	@SuppressWarnings("unused")
	private static final String TRANSLATION = "translation";

	private Survey survey;
	private QuestionGroup currentQuestionGroup;
	private Question currentQuestion;
	private Option currentOption;
	private Dependency currentDependency;
	private ArrayList<Option> currentOptions;
	private ValidationRule currentValidation;
	private AltText currentAltText;

	private StringBuilder builder;

	public Survey getSurvey() {
		return survey;
	}

	public void characters(char[] ch, int start, int length)
			throws SAXException {
		super.characters(ch, start, length);
		builder.append(ch, start, length);
	}

	/**
	 * processes elements after the end tag is encountered
	 */
	public void endElement(String uri, String localName, String name)
			throws SAXException {
		super.endElement(uri, localName, name);
		if (this.currentQuestionGroup != null) {
			if (localName.equalsIgnoreCase(HEADING)) {
				currentQuestionGroup.setHeading(builder.toString().trim());
			} else if (localName.equalsIgnoreCase(QUESTION)) {
				currentQuestionGroup.addQuestion(currentQuestion);
				currentQuestion = null;
			} else if (localName.equalsIgnoreCase(QUESTION_GROUP)) {
				survey.addQuestionGroup(currentQuestionGroup);
				currentQuestionGroup = null;
			}
		}
		if (currentQuestion != null) {
			// <text> can appear multiple places. We need to make sure we're not
			// in the context of an option here
			if (localName.equalsIgnoreCase(TEXT) && currentOption == null) {
				currentQuestion.setText(builder.toString().trim());
			} else if (localName.equalsIgnoreCase(OPTIONS)) {
				currentQuestion.setOptions(currentOptions);
				currentOptions = null;
			} else if (localName.equalsIgnoreCase(TIP)) {
				currentQuestion.setTip(builder.toString().trim());
			} else if (localName.equalsIgnoreCase(VALIDATION_RULE)) {
				currentQuestion.setValidationRule(currentValidation);
				currentValidation = null;
			}
		}
		if (currentOption != null) {
			// the null check here is to handle "old" style options that don't
			// have a <text> element
			if (localName.equalsIgnoreCase(OPTION)
					&& currentOption.getText() == null) {
				currentOption.setText(builder.toString().trim());
				if (currentOptions != null) {
					currentOptions.add(currentOption);
				}
				currentOption = null;
			}
			// handle "new" style options that have a <text> element
			if (localName.equalsIgnoreCase(TEXT)) {
				currentOption.setText(builder.toString().trim());
				if (currentOptions != null) {
					currentOptions.add(currentOption);
				}
			}
			// close the current option
			if (localName.equalsIgnoreCase(OPTION)) {
				currentOption = null;
			}
		}
		if (currentAltText != null) {
			currentAltText.setText(builder.toString().trim());
			if (currentOption != null) {
				currentOption.addAltText(currentAltText);
			} else if (currentQuestion != null) {
				currentQuestion.addAltText(currentAltText);
			}
			currentAltText = null;
		}
		builder.setLength(0);
	}

	/**
	 * construct a new survey object and store as a member
	 */
	public void startDocument() throws SAXException {
		super.startDocument();
		survey = new Survey();
		builder = new StringBuilder();
	}

	/**
	 * read in the attributes of the new xml element and set the appropriate
	 * values on the object(s) being hydrated.
	 */
	public void startElement(String uri, String localName, String name,
			Attributes attributes) throws SAXException {
		super.startElement(uri, localName, name, attributes);
		if (localName.equalsIgnoreCase(QUESTION_GROUP)) {
			currentQuestionGroup = new QuestionGroup();
			if (attributes.getValue(ORDER) != null) {
				currentQuestionGroup.setOrder(Integer.parseInt(attributes
						.getValue(ORDER)));
			} else {
				int count = 1;
				if (survey != null && survey.getQuestionGroups() != null) {
					count = survey.getQuestionGroups().size() + 2;
				}
				currentQuestionGroup.setOrder(count);
			}
		} else if (localName.equalsIgnoreCase(QUESTION)) {
			currentQuestion = new Question();
			if (attributes.getValue(ORDER) != null) {
				currentQuestion.setOrder(Integer.parseInt(attributes
						.getValue(ORDER)));
			} else {
				int count = 1;
				if (currentQuestionGroup != null
						&& currentQuestionGroup.getQuestions() != null) {
					count = currentQuestionGroup.getQuestions().size() + 2;
				}
				currentQuestion.setOrder(count);
			}
			if (attributes.getValue(MANDATORY) != null) {
				currentQuestion.setMandatory(Boolean.parseBoolean(attributes
						.getValue(MANDATORY)));
			} else {
				currentQuestion.setMandatory(false);
			}
			if (attributes.getValue(LOCKED) != null) {
				currentQuestion.setLocked(Boolean.parseBoolean(attributes
						.getValue(LOCKED)));
			} else {
				currentQuestion.setLocked(false);
			}
			
			currentQuestion.setType(attributes.getValue(TYPE));
			currentQuestion.setId(attributes.getValue(ID));
			String validation = attributes.getValue(VALIDATION_TYPE);
			if (validation != null && validation.trim().length() > 0) {
				currentQuestion
						.setValidationRule(new ValidationRule(validation));
			}
		} else if (localName.equalsIgnoreCase(OPTIONS)) {
			currentOptions = new ArrayList<Option>();
			if (currentQuestion != null) {
				if (attributes.getValue(ALLOW_OTHER) != null) {
					currentQuestion.setAllowOther(Boolean
							.parseBoolean(attributes.getValue(ALLOW_OTHER)));
				} else {
					currentQuestion.setAllowOther(false);
				}
				currentQuestion.setRenderType(attributes.getValue(RENDER_TYPE));
				if (attributes.getValue(ALLOW_MULT) != null) {
					currentQuestion.setAllowMultiple(Boolean
							.parseBoolean(attributes.getValue(ALLOW_MULT)));
				} else {
					currentQuestion.setAllowMultiple(false);
				}
			}
		} else if (localName.equalsIgnoreCase(OPTION)) {
			currentOption = new Option();
			currentOption.setValue(attributes.getValue(VALUE));
		} else if (localName.equalsIgnoreCase(DEPENDENCY)) {
			currentDependency = new Dependency();
			currentDependency.setQuestion(attributes.getValue(QUESTION));
			currentDependency.setAnswer(attributes.getValue(ANSWER));
			if (currentQuestion != null) {
				currentQuestion.addDependency(currentDependency);
			}
			currentDependency = null;
		} else if (localName.equalsIgnoreCase(VALIDATION_RULE)) {
			currentValidation = new ValidationRule(attributes
					.getValue(VALIDATION_TYPE));
			currentValidation.setAllowDecimal(attributes.getValue(ALLOW_DEC));
			currentValidation.setAllowSigned(attributes.getValue(ALLOW_SIGN));
			currentValidation.setMaxLength(attributes.getValue(MAX_LENGTH));
			currentValidation.setMinVal(attributes.getValue(MIN_VAL));
			currentValidation.setMaxVal(attributes.getValue(MAX_VAL));
		} else if (localName.equalsIgnoreCase(VIDEO)) {
			if (currentQuestion != null) {
				currentQuestion.setVideo(attributes.getValue(SRC));
			}
		} else if (localName.equalsIgnoreCase(IMAGE)) {
			if (currentQuestion != null) {
				currentQuestion.addImage(attributes.getValue(SRC));
				currentQuestion.addImageCaption(attributes.getValue(CAPTION));
			}
		} else if (localName.equalsIgnoreCase(ALT_TEXT)) {
			currentAltText = new AltText();
			currentAltText.setLanguage(attributes.getValue(LANG));
			currentAltText.setType(attributes.getValue(TYPE));
		}
	}
}
