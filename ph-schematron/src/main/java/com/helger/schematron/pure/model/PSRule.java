/**
 * Copyright (C) 2014-2017 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.schematron.pure.model;

import java.util.Map;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.ext.CommonsArrayList;
import com.helger.commons.collection.ext.CommonsLinkedHashMap;
import com.helger.commons.collection.ext.ICommonsList;
import com.helger.commons.collection.ext.ICommonsOrderedMap;
import com.helger.commons.string.StringHelper;
import com.helger.commons.string.ToStringGenerator;
import com.helger.schematron.CSchematron;
import com.helger.schematron.CSchematronXML;
import com.helger.schematron.pure.errorhandler.IPSErrorHandler;
import com.helger.xml.microdom.IMicroElement;
import com.helger.xml.microdom.MicroElement;

/**
 * A single Schematron rule-element.<br>
 * A list of assertions tested within the context specified by the required
 * context attribute. The context attribute specifies the rule context
 * expression.<br>
 * NOTE: It is not an error if a rule never fires in a document. In order to
 * test that a document always has some context, a new pattern should be created
 * from the context of the document, with an assertion requiring the element or
 * attribute.<br>
 * The icon, see and fpi attributes allow rich interfaces and documentation.<br>
 * The flag attribute allows more detailed outcomes.<br>
 * The role and subject attributes allow explicit identification of some part of
 * a pattern as part of the validation outcome.<br>
 * When the rule element has the attribute abstract with a value true, then the
 * rule is an abstract rule. An abstract rule shall not have a context
 * attribute. An abstract rule is a list of assertions that will be invoked by
 * other rules belonging to the same pattern using the extends element. Abstract
 * rules provide a mechanism for reducing schema size.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public class PSRule implements
                    IPSElement,
                    IPSHasID,
                    IPSHasFlag,
                    IPSHasForeignElements,
                    IPSHasIncludes,
                    IPSHasLets,
                    IPSHasRichGroup,
                    IPSHasLinkableGroup
{
  public static final boolean DEFAULT_ABSTRACT = false;

  private String m_sFlag;
  private PSRichGroup m_aRich;
  private PSLinkableGroup m_aLinkable;
  private boolean m_bAbstract = DEFAULT_ABSTRACT;
  private String m_sContext;
  private String m_sID;
  private final ICommonsList <PSInclude> m_aIncludes = new CommonsArrayList <> ();
  private final ICommonsList <PSLet> m_aLets = new CommonsArrayList <> ();
  private final ICommonsList <IPSElement> m_aContent = new CommonsArrayList <> ();
  private ICommonsOrderedMap <String, String> m_aForeignAttrs;
  private ICommonsList <IMicroElement> m_aForeignElements;

  public PSRule ()
  {}

  public boolean isValid (@Nonnull final IPSErrorHandler aErrorHandler)
  {
    // abstract rules need an ID
    if (m_bAbstract && StringHelper.hasNoText (m_sID))
    {
      aErrorHandler.error (this, "abstract <rule> has no 'id'");
      return false;
    }
    // abstract rules may not have a context
    if (m_bAbstract && StringHelper.hasText (m_sContext))
    {
      aErrorHandler.error (this, "abstract <rule> may not have a 'context'");
      return false;
    }
    // Non-abstract rules need a context
    if (!m_bAbstract && StringHelper.hasNoText (m_sContext))
    {
      aErrorHandler.error (this, "<rule> must have a 'context'");
      return false;
    }
    // At least one assert, report or extends must be present
    if (m_aContent.isEmpty ())
    {
      aErrorHandler.error (this, "<rule> has no content");
      return false;
    }
    for (final PSInclude aInclude : m_aIncludes)
      if (!aInclude.isValid (aErrorHandler))
        return false;
    for (final PSLet aLet : m_aLets)
      if (!aLet.isValid (aErrorHandler))
        return false;
    for (final IPSElement aContent : m_aContent)
      if (!aContent.isValid (aErrorHandler))
        return false;
    return true;
  }

  public void validateCompletely (@Nonnull final IPSErrorHandler aErrorHandler)
  {
    // abstract rules need an ID
    if (m_bAbstract && StringHelper.hasNoText (m_sID))
      aErrorHandler.error (this, "abstract <rule> has no 'id'");
    // abstract rules may not have a context
    if (m_bAbstract && StringHelper.hasText (m_sContext))
      aErrorHandler.error (this, "abstract <rule> may not have a 'context'");
    // Non-abstract rules need a context
    if (!m_bAbstract && StringHelper.hasNoText (m_sContext))
      aErrorHandler.error (this, "<rule> must have a 'context'");
    // At least one assert, report or extends must be present
    if (m_aContent.isEmpty ())
      aErrorHandler.error (this, "<rule> has no content");
    for (final PSInclude aInclude : m_aIncludes)
      aInclude.validateCompletely (aErrorHandler);
    for (final PSLet aLet : m_aLets)
      aLet.validateCompletely (aErrorHandler);
    for (final IPSElement aContent : m_aContent)
      aContent.validateCompletely (aErrorHandler);
  }

  public boolean isMinimal ()
  {
    for (final PSInclude aInclude : m_aIncludes)
      if (!aInclude.isMinimal ())
        return false;
    for (final PSLet aLet : m_aLets)
      if (!aLet.isMinimal ())
        return false;
    for (final IPSElement aContent : m_aContent)
      if (!aContent.isMinimal ())
        return false;
    return true;
  }

  public void addForeignElement (@Nonnull final IMicroElement aForeignElement)
  {
    ValueEnforcer.notNull (aForeignElement, "ForeignElement");
    if (aForeignElement.hasParent ())
      throw new IllegalArgumentException ("ForeignElement already has a parent!");
    if (m_aForeignElements == null)
      m_aForeignElements = new CommonsArrayList <> ();
    m_aForeignElements.add (aForeignElement);
  }

  public boolean hasForeignElements ()
  {
    return m_aForeignElements != null && m_aForeignElements.isNotEmpty ();
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <IMicroElement> getAllForeignElements ()
  {
    return new CommonsArrayList <> (m_aForeignElements);
  }

  public void addForeignAttribute (@Nonnull final String sAttrName, @Nonnull final String sAttrValue)
  {
    ValueEnforcer.notNull (sAttrName, "AttrName");
    ValueEnforcer.notNull (sAttrValue, "AttrValue");
    if (m_aForeignAttrs == null)
      m_aForeignAttrs = new CommonsLinkedHashMap <> ();
    m_aForeignAttrs.put (sAttrName, sAttrValue);
  }

  public boolean hasForeignAttributes ()
  {
    return m_aForeignAttrs != null && m_aForeignAttrs.isNotEmpty ();
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <String, String> getAllForeignAttributes ()
  {
    return new CommonsLinkedHashMap <> (m_aForeignAttrs);
  }

  public void setFlag (@Nullable final String sFlag)
  {
    m_sFlag = sFlag;
  }

  @Nullable
  public String getFlag ()
  {
    return m_sFlag;
  }

  public void setRich (@Nullable final PSRichGroup aRich)
  {
    m_aRich = aRich;
  }

  @Nullable
  public PSRichGroup getRich ()
  {
    return m_aRich;
  }

  public void setLinkable (@Nullable final PSLinkableGroup aLinkable)
  {
    m_aLinkable = aLinkable;
  }

  @Nullable
  public PSLinkableGroup getLinkable ()
  {
    return m_aLinkable;
  }

  /**
   * @param bAbstract
   *        The abstract state of this rule.
   */
  public void setAbstract (final boolean bAbstract)
  {
    m_bAbstract = bAbstract;
  }

  /**
   * @return <code>true</code> if this rule is abstract, <code>false</code>
   *         otherwise. Default is {@value #DEFAULT_ABSTRACT}.
   */
  public boolean isAbstract ()
  {
    return m_bAbstract;
  }

  public void setContext (@Nullable final String sContext)
  {
    m_sContext = sContext;
  }

  @Nullable
  public String getContext ()
  {
    return m_sContext;
  }

  public void setID (@Nullable final String sID)
  {
    m_sID = sID;
  }

  @Nullable
  public String getID ()
  {
    return m_sID;
  }

  public void addInclude (@Nonnull final PSInclude aInclude)
  {
    ValueEnforcer.notNull (aInclude, "Include");
    m_aIncludes.add (aInclude);
  }

  public boolean hasAnyInclude ()
  {
    return m_aIncludes.isNotEmpty ();
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <PSInclude> getAllIncludes ()
  {
    return m_aIncludes.getClone ();
  }

  public void addLet (@Nonnull final PSLet aLet)
  {
    ValueEnforcer.notNull (aLet, "Let");
    m_aLets.add (aLet);
  }

  public boolean hasAnyLet ()
  {
    return m_aLets.isNotEmpty ();
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <PSLet> getAllLets ()
  {
    return m_aLets.getClone ();
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <String, String> getAllLetsAsMap ()
  {
    final ICommonsOrderedMap <String, String> ret = new CommonsLinkedHashMap <> ();
    for (final PSLet aLet : m_aLets)
      ret.put (aLet.getName (), aLet.getValue ());
    return ret;
  }

  public void addAssertReport (@Nonnull final PSAssertReport aAssertReport)
  {
    ValueEnforcer.notNull (aAssertReport, "AssertReport");
    m_aContent.add (aAssertReport);
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <PSAssertReport> getAllAssertReports ()
  {
    return m_aContent.getAllInstanceOf (PSAssertReport.class);
  }

  public void addExtends (@Nonnull final PSExtends aExtends)
  {
    ValueEnforcer.notNull (aExtends, "Extends");
    m_aContent.add (aExtends);
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <PSExtends> getAllExtends ()
  {
    return m_aContent.getAllInstanceOf (PSExtends.class);
  }

  @Nonnegative
  public int getExtendsCount ()
  {
    return m_aContent.getCount (e -> e instanceof PSExtends);
  }

  public boolean hasAnyExtends ()
  {
    return m_aContent.containsAny (e -> e instanceof PSExtends);
  }

  /**
   * @return A list consisting of {@link PSAssertReport} and {@link PSExtends}
   *         parameters
   */
  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <IPSElement> getAllContentElements ()
  {
    return m_aContent.getClone ();
  }

  @Nonnull
  public IMicroElement getAsMicroElement ()
  {
    final IMicroElement ret = new MicroElement (CSchematron.NAMESPACE_SCHEMATRON, CSchematronXML.ELEMENT_RULE);
    ret.setAttribute (CSchematronXML.ATTR_FLAG, m_sFlag);
    if (m_bAbstract)
      ret.setAttribute (CSchematronXML.ATTR_ABSTRACT, "true");
    ret.setAttribute (CSchematronXML.ATTR_CONTEXT, m_sContext);
    ret.setAttribute (CSchematronXML.ATTR_ID, m_sID);
    if (m_aRich != null)
      m_aRich.fillMicroElement (ret);
    if (m_aLinkable != null)
      m_aLinkable.fillMicroElement (ret);
    if (m_aForeignElements != null)
      for (final IMicroElement aForeignElement : m_aForeignElements)
        ret.appendChild (aForeignElement.getClone ());
    for (final PSInclude aInclude : m_aIncludes)
      ret.appendChild (aInclude.getAsMicroElement ());
    for (final PSLet aLet : m_aLets)
      ret.appendChild (aLet.getAsMicroElement ());
    for (final IPSElement aContent : m_aContent)
      ret.appendChild (aContent.getAsMicroElement ());
    if (m_aForeignAttrs != null)
      for (final Map.Entry <String, String> aEntry : m_aForeignAttrs.entrySet ())
        ret.setAttribute (aEntry.getKey (), aEntry.getValue ());
    return ret;
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).appendIfNotNull ("flag", m_sFlag)
                                       .appendIfNotNull ("rich", m_aRich)
                                       .appendIfNotNull ("linkable", m_aLinkable)
                                       .append ("abstract", m_bAbstract)
                                       .appendIfNotNull ("context", m_sContext)
                                       .appendIfNotNull ("id", m_sID)
                                       .appendIf ("includes", m_aIncludes, CollectionHelper::isNotEmpty)
                                       .appendIf ("lets", m_aLets, CollectionHelper::isNotEmpty)
                                       .appendIf ("content", m_aContent, CollectionHelper::isNotEmpty)
                                       .appendIf ("foreignAttrs", m_aForeignAttrs, CollectionHelper::isNotEmpty)
                                       .appendIf ("foreignElements", m_aForeignElements, CollectionHelper::isNotEmpty)
                                       .getToString ();
  }
}
