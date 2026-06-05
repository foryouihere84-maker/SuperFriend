/**
 * PPTX Compiler - Compile multiple slide JS files into a single presentation
 *
 * Usage: node compile.js --slides_dir ./slides --output output.pptx
 */

const PptxGenJS = require('pptxgenjs');
const fs = require('fs');
const path = require('path');

// Parse command line arguments
function parseArgs() {
    const args = process.argv.slice(2);
    const params = {};

    for (let i = 0; i < args.length; i++) {
        if (args[i].startsWith('--')) {
            const key = args[i].slice(2);
            const value = args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : true;
            params[key] = value;
            i++;
        }
    }

    return params;
}

// Design system
const DESIGN = {
    layout: 'LAYOUT_16x9',
    fonts: {
        english: 'Arial',
        chinese: 'Microsoft YaHei'
    },
    colors: {
        primary: '2D5F8A',
        secondary: '5BA4E4',
        accent: 'FF6B6B',
        light: 'F5F5F5',
        bg: 'FFFFFF'
    }
};

// Compile slides
async function compilePresentation(params) {
    const pptx = new PptxGenJS();
    pptx.layout = DESIGN.layout;

    const slidesDir = params.slides_dir || './slides';
    const outputPath = params.output || 'output.pptx';

    // Check if slides directory exists
    if (!fs.existsSync(slidesDir)) {
        console.error(`Slides directory not found: ${slidesDir}`);
        process.exit(1);
    }

    // Get all slide files
    const slideFiles = fs.readdirSync(slidesDir)
        .filter(f => f.endsWith('.js') || f.endsWith('.json'))
        .sort();

    if (slideFiles.length === 0) {
        console.warn('No slide files found in directory');
        // Create empty presentation
        const slide = pptx.addSlide();
        slide.addText('No slides', {
            x: 0, y: 2.5, w: '100%', h: 1,
            fontSize: 24, align: 'center', color: DESIGN.colors.primary
        });
    } else {
        console.log(`Found ${slideFiles.length} slide files`);

        for (const slideFile of slideFiles) {
            const slidePath = path.join(slidesDir, slideFile);
            console.log(`Processing: ${slideFile}`);

            try {
                let slideConfig;

                if (slideFile.endsWith('.json')) {
                    slideConfig = JSON.parse(fs.readFileSync(slidePath, 'utf-8'));
                } else {
                    // For JS files, require them (they should export a slide config)
                    delete require.cache[require.resolve(slidePath)];
                    const slideModule = require(slidePath);
                    slideConfig = slideModule.slide || slideModule;
                }

                // Add slide
                const slide = pptx.addSlide();

                // Apply slide configuration
                if (slideConfig.background) {
                    slide.background = { color: slideConfig.background };
                }

                if (slideConfig.elements) {
                    for (const elem of slideConfig.elements) {
                        addElement(slide, elem);
                    }
                }

                if (slideConfig.text) {
                    slide.addText(slideConfig.text.content || slideConfig.text, {
                        x: slideConfig.text.x || 0.5,
                        y: slideConfig.text.y || 0.5,
                        w: slideConfig.text.w || 9,
                        h: slideConfig.text.h || 1,
                        fontSize: slideConfig.text.fontSize || 24,
                        fontFace: slideConfig.text.fontFace || DESIGN.fonts.english,
                        color: slideConfig.text.color || DESIGN.colors.primary,
                        align: slideConfig.text.align || 'left'
                    });
                }

            } catch (error) {
                console.error(`Error processing ${slideFile}:`, error.message);
            }
        }
    }

    // Save presentation
    await pptx.writeFile({ fileName: outputPath });
    console.log(`Presentation compiled: ${outputPath}`);

    return outputPath;
}

// Add element to slide
function addElement(slide, elem) {
    switch (elem.type) {
        case 'text':
            slide.addText(elem.text, {
                x: elem.x || 0,
                y: elem.y || 0,
                w: elem.w || '100%',
                h: elem.h || 1,
                fontSize: elem.fontSize || 18,
                fontFace: elem.fontFace || DESIGN.fonts.english,
                color: elem.color || DESIGN.colors.primary,
                align: elem.align || 'left',
                bold: elem.bold || false,
                italic: elem.italic || false
            });
            break;

        case 'title':
            slide.addText(elem.text, {
                x: elem.x || 0.5,
                y: elem.y || 0.5,
                w: elem.w || 9,
                h: elem.h || 1,
                fontSize: elem.fontSize || 36,
                fontFace: elem.fontFace || DESIGN.fonts.english,
                color: elem.color || DESIGN.colors.primary,
                align: elem.align || 'left',
                bold: true
            });
            break;

        case 'image':
            slide.addImage({
                path: elem.path,
                x: elem.x || 0,
                y: elem.y || 0,
                w: elem.w || 2,
                h: elem.h || 2
            });
            break;

        case 'table':
            slide.addTable(elem.rows, {
                x: elem.x || 0.5,
                y: elem.y || 0.5,
                w: elem.w || 9,
                colW: elem.colW,
                border: elem.border || { pt: 1, color: DESIGN.colors.primary },
                fontFace: elem.fontFace || DESIGN.fonts.english,
                fontSize: elem.fontSize || 12
            });
            break;

        case 'list':
            slide.addText(elem.items.map(item => ({ text: item, options: { bullet: true } })), {
                x: elem.x || 0.5,
                y: elem.y || 0.5,
                w: elem.w || 9,
                h: elem.h || 4,
                fontSize: elem.fontSize || 18,
                fontFace: elem.fontFace || DESIGN.fonts.english,
                color: elem.color || DESIGN.colors.primary
            });
            break;
    }
}

// Main
async function main() {
    const params = parseArgs();

    try {
        await compilePresentation(params);
    } catch (error) {
        console.error('Error compiling presentation:', error.message);
        process.exit(1);
    }
}

main();