/**
 * PPTX Creator - Create PowerPoint presentations from scratch using PptxGenJS
 *
 * Usage: node create.js --output presentation.pptx --slides slides.json
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

// Design system defaults
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

// Create presentation
async function createPresentation(params) {
    const pptx = new PptxGenJS();

    // Set layout
    pptx.layout = DESIGN.layout;

    // Define theme
    pptx.defineLayout({ name: 'CUSTOM', width: 10, height: 5.625 });

    // Get slides data
    let slidesData = [];
    if (params.slides) {
        const slidesPath = path.resolve(params.slides);
        if (fs.existsSync(slidesPath)) {
            slidesData = JSON.parse(fs.readFileSync(slidesPath, 'utf-8'));
        }
    }

    // If no slides data, create a basic presentation
    if (slidesData.length === 0) {
        // Title slide
        const titleSlide = pptx.addSlide();
        titleSlide.addText(params.title || 'Presentation', {
            x: 0.5,
            y: 2,
            w: 9,
            h: 1,
            fontSize: 36,
            fontFace: DESIGN.fonts.english,
            color: DESIGN.colors.primary,
            align: 'center'
        });

        if (params.subtitle) {
            titleSlide.addText(params.subtitle, {
                x: 0.5,
                y: 3,
                w: 9,
                h: 0.5,
                fontSize: 18,
                fontFace: DESIGN.fonts.english,
                color: DESIGN.colors.secondary,
                align: 'center'
            });
        }
    } else {
        // Add slides from data
        for (const slideData of slidesData) {
            const slide = pptx.addSlide();

            // Apply background if specified
            if (slideData.background) {
                slide.background = { color: slideData.background };
            }

            // Add content
            if (slideData.elements) {
                for (const elem of slideData.elements) {
                    if (elem.type === 'text') {
                        slide.addText(elem.text, {
                            x: elem.x || 0,
                            y: elem.y || 0,
                            w: elem.w || '100%',
                            h: elem.h || 1,
                            fontSize: elem.fontSize || 18,
                            fontFace: elem.fontFace || DESIGN.fonts.english,
                            color: elem.color || DESIGN.colors.primary,
                            align: elem.align || 'left',
                            bold: elem.bold || false
                        });
                    } else if (elem.type === 'image') {
                        slide.addImage({
                            path: elem.path,
                            x: elem.x || 0,
                            y: elem.y || 0,
                            w: elem.w || 2,
                            h: elem.h || 2
                        });
                    } else if (elem.type === 'table') {
                        slide.addTable(elem.rows, {
                            x: elem.x || 0.5,
                            y: elem.y || 0.5,
                            w: elem.w || 9,
                            colW: elem.colW,
                            border: elem.border || { pt: 1, color: DESIGN.colors.primary },
                            fontFace: elem.fontFace || DESIGN.fonts.english,
                            fontSize: elem.fontSize || 12
                        });
                    }
                }
            }
        }
    }

    // Save presentation
    const outputPath = params.output || 'presentation.pptx';
    await pptx.writeFile({ fileName: outputPath });

    console.log(`Presentation created: ${outputPath}`);
    return outputPath;
}

// Main
async function main() {
    const params = parseArgs();

    try {
        await createPresentation(params);
    } catch (error) {
        console.error('Error creating presentation:', error.message);
        process.exit(1);
    }
}

main();