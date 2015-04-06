/**
 * Copyright (C) 2014-2015 Philip Helger (www.helger.com)
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
package com.helger.schematron.xslt;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMResult;

import org.oclc.purl.dsdl.svrl.SchematronOutputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotations.ReturnsMutableCopy;
import com.helger.commons.collections.CollectionHelper;
import com.helger.commons.io.IInputStreamProvider;
import com.helger.commons.io.IReadableResource;
import com.helger.commons.io.streams.StreamUtils;
import com.helger.commons.state.EValidity;
import com.helger.commons.string.ToStringGenerator;
import com.helger.commons.xml.XMLFactory;
import com.helger.commons.xml.serialize.XMLWriter;
import com.helger.commons.xml.transform.LoggingTransformErrorListener;
import com.helger.commons.xml.transform.TransformSourceFactory;
import com.helger.schematron.AbstractSchematronResource;
import com.helger.schematron.svrl.SVRLReader;
import com.helger.schematron.xslt.validator.ISchematronXSLTValidator;
import com.helger.schematron.xslt.validator.SchematronXSLTValidatorDefault;

/**
 * Abstract implementation of a Schematron resource that is based on XSLT
 * transformations.
 *
 * @author Philip Helger
 */
@NotThreadSafe
public abstract class AbstractSchematronXSLTBasedResource extends AbstractSchematronResource
{
  private static final Logger s_aLogger = LoggerFactory.getLogger (AbstractSchematronXSLTBasedResource.class);

  protected ErrorListener m_aCustomErrorListener;
  protected URIResolver m_aCustomURIResolver;
  protected Map <String, ?> m_aCustomParameters;
  private ISchematronXSLTValidator m_aXSLTValidator = new SchematronXSLTValidatorDefault ();

  public AbstractSchematronXSLTBasedResource (@Nonnull final IReadableResource aSCHResource)
  {
    super (aSCHResource);
  }

  @Nullable
  public ErrorListener getErrorListener ()
  {
    return m_aCustomErrorListener;
  }

  public void setErrorListener (@Nullable final ErrorListener aCustomErrorListener)
  {
    m_aCustomErrorListener = aCustomErrorListener;
  }

  @Nullable
  public URIResolver getURIResolver ()
  {
    return m_aCustomURIResolver;
  }

  public void setURIResolver (@Nullable final URIResolver aCustomURIResolver)
  {
    m_aCustomURIResolver = aCustomURIResolver;
  }

  public boolean hasParameters ()
  {
    return CollectionHelper.isNotEmpty (m_aCustomParameters);
  }

  @Nonnull
  @ReturnsMutableCopy
  public Map <String, ?> getParameters ()
  {
    return CollectionHelper.newOrderedMap (m_aCustomParameters);
  }

  public void setParameters (@Nullable final Map <String, ?> aCustomParameters)
  {
    m_aCustomParameters = CollectionHelper.newOrderedMap (aCustomParameters);
  }

  /**
   * @return The XSLT provider passed in the constructor. May be
   *         <code>null</code>.
   */
  @Nullable
  public abstract ISchematronXSLTBasedProvider getXSLTProvider ();

  /**
   * @return The XSLT validator to be used. Never <code>null</code>.
   */
  @Nonnull
  public ISchematronXSLTValidator getXSLTValidator ()
  {
    return m_aXSLTValidator;
  }

  public void setXSLTValidator (@Nonnull final ISchematronXSLTValidator aXSLTValidator)
  {
    ValueEnforcer.notNull (aXSLTValidator, "XSLTValidator");
    m_aXSLTValidator = aXSLTValidator;
  }

  public final boolean isValidSchematron ()
  {
    final ISchematronXSLTBasedProvider aXSLTProvider = getXSLTProvider ();
    return aXSLTProvider != null && aXSLTProvider.isValidSchematron ();
  }

  @Nonnull
  public EValidity getSchematronValidity (@Nonnull final IInputStreamProvider aXMLResource) throws Exception
  {
    ValueEnforcer.notNull (aXMLResource, "XMLResource");

    final InputStream aIS = aXMLResource.getInputStream ();
    if (aIS == null)
    {
      // Resource not found
      s_aLogger.warn ("XML resource " + aXMLResource + " does not exist!");
      return EValidity.INVALID;
    }

    try
    {
      // InputStream to Source
      return getSchematronValidity (TransformSourceFactory.create (aIS));
    }
    finally
    {
      // Ensure InputStream is closed
      StreamUtils.close (aIS);
    }
  }

  @Nonnull
  public EValidity getSchematronValidity (@Nonnull final Source aXMLSource) throws Exception
  {
    // We don't have a short circuit here - apply the full validation
    final SchematronOutputType aSO = applySchematronValidationToSVRL (aXMLSource);
    if (aSO == null)
      return EValidity.INVALID;

    // And now filter all elements that make the passed source invalid
    return m_aXSLTValidator.getSchematronValidity (aSO);
  }

  @Nullable
  public Document applySchematronValidation (@Nonnull final IInputStreamProvider aXMLResource) throws Exception
  {
    ValueEnforcer.notNull (aXMLResource, "XMLResource");

    final InputStream aIS = aXMLResource.getInputStream ();
    if (aIS == null)
    {
      // Resource not found
      s_aLogger.warn ("XML resource " + aXMLResource + " does not exist!");
      return null;
    }

    try
    {
      return applySchematronValidation (TransformSourceFactory.create (aIS));
    }
    finally
    {
      StreamUtils.close (aIS);
    }
  }

  @Nullable
  public final Document applySchematronValidation (@Nonnull final Source aXMLSource) throws Exception
  {
    ValueEnforcer.notNull (aXMLSource, "XMLSource");

    final ISchematronXSLTBasedProvider aXSLTProvider = getXSLTProvider ();
    if (aXSLTProvider == null || !aXSLTProvider.isValidSchematron ())
      return null;

    // Create result document
    final Document ret = XMLFactory.newDocument ();

    // Create the transformer object from the templates specified in the
    // constructor
    final Transformer aTransformer = aXSLTProvider.getXSLTTemplates ().newTransformer ();

    // Apply customizations
    // Ensure an error listener is present
    if (m_aCustomErrorListener != null)
      aTransformer.setErrorListener (m_aCustomErrorListener);
    else
      aTransformer.setErrorListener (new LoggingTransformErrorListener (Locale.US));

    // Set the optional URI Resolver
    if (m_aCustomURIResolver != null)
      aTransformer.setURIResolver (m_aCustomURIResolver);

    // Set all custom parameters
    if (m_aCustomParameters != null)
      for (final Map.Entry <String, ?> aEntry : m_aCustomParameters.entrySet ())
        aTransformer.setParameter (aEntry.getKey (), aEntry.getValue ());

    // Debug print the created XSLT document
    if (false)
      System.out.println (XMLWriter.getXMLString (aXSLTProvider.getXSLTDocument ()));

    // Do the main transformation
    aTransformer.transform (aXMLSource, new DOMResult (ret));

    // Debug print the created SVRL document
    if (false)
      System.out.println (XMLWriter.getXMLString (ret));

    return ret;
  }

  @Nullable
  public SchematronOutputType applySchematronValidationToSVRL (@Nonnull final IInputStreamProvider aXMLResource) throws Exception
  {
    final Document aDoc = applySchematronValidation (aXMLResource);
    return aDoc == null ? null : SVRLReader.readXML (aDoc);
  }

  @Nullable
  public SchematronOutputType applySchematronValidationToSVRL (@Nonnull final Source aXMLSource) throws Exception
  {
    final Document aDoc = applySchematronValidation (aXMLSource);
    return aDoc == null ? null : SVRLReader.readXML (aDoc);
  }

  @Override
  public String toString ()
  {
    return ToStringGenerator.getDerived (super.toString ()).append ("XSLTValidator", m_aXSLTValidator).toString ();
  }
}